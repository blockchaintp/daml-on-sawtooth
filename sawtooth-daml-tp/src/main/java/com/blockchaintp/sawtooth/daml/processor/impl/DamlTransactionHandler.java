package com.blockchaintp.sawtooth.daml.processor.impl;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import com.blockchaintp.sawtooth.daml.processor.DamlCommitter;
import com.blockchaintp.sawtooth.daml.processor.LedgerState;
import com.blockchaintp.sawtooth.daml.protobuf.SawtoothDamlTransaction;
import com.blockchaintp.sawtooth.daml.util.KeyValueUtils;
import com.blockchaintp.sawtooth.daml.util.Namespace;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntry;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntryId;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlStateKey;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlStateValue;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlSubmission;
import com.daml.ledger.participant.state.kvutils.KeyValueSubmission;
import com.daml.ledger.participant.state.v1.Configuration;
import com.digitalasset.daml.lf.data.Time.Timestamp;
import com.digitalasset.platform.services.time.TimeModel;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import sawtooth.sdk.processor.Context;
import sawtooth.sdk.processor.TransactionHandler;
import sawtooth.sdk.processor.exceptions.InternalError;
import sawtooth.sdk.processor.exceptions.InvalidTransactionException;
import sawtooth.sdk.protobuf.TpProcessRequest;
import sawtooth.sdk.protobuf.TransactionHeader;
import scala.Tuple2;

/**
 * A TransactionHandler implementation which handles DAML.
 * @author scealiontach
 */
public final class DamlTransactionHandler implements TransactionHandler {

  private final DamlCommitter committer;
  private final String familyName;
  private final String namespace;

  private final String version;

  /**
   * Constructs a TransactionHandler for DAML Transactions.
   * @param damlCommitter the DamlCommitter which will be used to process
   *                      submissions
   */
  public DamlTransactionHandler(final DamlCommitter damlCommitter) {
    this.committer = damlCommitter;
    this.namespace = Namespace.getNameSpace();
    this.version = Namespace.DAML_FAMILY_VERSION_1_0;
    this.familyName = Namespace.DAML_FAMILY_NAME;
  }

  @Override
  public void apply(final TpProcessRequest tpProcessRequest, final Context state)
      throws InvalidTransactionException, InternalError {

    basicRequestChecks(tpProcessRequest);

    LedgerState ledgerState = new DamlLedgerState(state);
    SawtoothDamlTransaction tx;
    TransactionHeader txHeader = tpProcessRequest.getHeader();
    DamlSubmission submission;
    try {
      tx = SawtoothDamlTransaction.parseFrom(tpProcessRequest.getPayload());
      submission = KeyValueSubmission.unpackDamlSubmission(tx.getSubmission());
    } catch (InvalidProtocolBufferException e) {
      InvalidTransactionException ite = new InvalidTransactionException(
          String.format("Payload is unparseable, and not a valid DamlSubmission", e.getMessage().getBytes()));
      ite.initCause(e);
      throw ite;
    }

    Map<DamlStateKey, DamlStateValue> stateMap = buildStateMap(ledgerState, txHeader, submission);

    Map<DamlLogEntryId, DamlLogEntry> inputLogEntries = buildLogEntryMap(ledgerState, txHeader, submission);

    // 2. from the submission compute the transaction deltas ( contracts to be
    // removed in txDelta.inputs, contracts to be added in txDelta.outputs )
    // At this point all contract_ids may be assumed to be absolute, in fact they
    // need to be since all inputs and outputs need
    // to be known before the transaction is sent to the validator.

    // 3. Validate submission with DAML Engine

    DamlLogEntryId entryId = DamlLogEntryId.newBuilder()
        .setEntryId(ByteString.copyFromUtf8(UUID.randomUUID().toString())).build();

    recordState(ledgerState, submission, inputLogEntries, stateMap, entryId);
  }

  /**
   * Fundamental checks of the transaction.
   * @param tpProcessRequest the process request
   * @throws InvalidTransactionException if the transaction fails because of a
   *                                     business rule validation error
   * @throws InternalError               if the transaction fails because of a
   *                                     system error
   */
  private void basicRequestChecks(final TpProcessRequest tpProcessRequest)
      throws InvalidTransactionException, InternalError {
    TransactionHeader header = tpProcessRequest.getHeader();
    if (header == null) {
      throw new InvalidTransactionException("Header expected");
    }

    ByteString payload = tpProcessRequest.getPayload();
    if (payload.size() == 0) {
      throw new InvalidTransactionException("Empty payload");
    }

    if (!header.getFamilyName().equals(this.familyName)) {
      throw new InvalidTransactionException("Family name does not match");
    }

    if (!header.getFamilyVersion().contentEquals(this.version)) {
      throw new InvalidTransactionException("Version does not match");
    }

  }

  private Map<DamlLogEntryId, DamlLogEntry> buildLogEntryMap(final LedgerState ledgerState,
      final TransactionHeader txHeader, final DamlSubmission submission)
      throws InternalError, InvalidTransactionException {
    List<String> inputList = txHeader.getInputsList();
    Map<DamlLogEntryId, String> inputLogEntryKeys = KeyValueUtils.submissionToLogAddressMap(submission);

    if (!inputList.containsAll(inputLogEntryKeys.values())) {
      throw new InvalidTransactionException(String.format("Not all LogEntryId's were declared as inputs"));
    }
    Map<DamlLogEntryId, DamlLogEntry> inputLogEntries = ledgerState.getDamlLogEntries(inputLogEntryKeys.keySet());
    return inputLogEntries;
  }

  private Map<DamlStateKey, DamlStateValue> buildStateMap(final LedgerState ledgerState,
      final TransactionHeader txHeader, final DamlSubmission submission)
      throws InvalidTransactionException, InternalError {
    Map<DamlStateKey, String> inputContractKeys = KeyValueUtils.submissionToDamlStateAddress(submission,
        DamlStateKey.KeyCase.CONTRACT_ID);
    Map<DamlStateKey, String> inputPackageKeys = KeyValueUtils.submissionToDamlStateAddress(submission,
        DamlStateKey.KeyCase.PACKAGE_ID);
    Map<DamlStateKey, String> inputCommandDedupKeys = KeyValueUtils.submissionToDamlStateAddress(submission,
        DamlStateKey.KeyCase.COMMAND_DEDUP);

    List<String> inputList = txHeader.getInputsList();
    if (!inputList.containsAll(inputCommandDedupKeys.values())) {
      throw new InvalidTransactionException(String.format("Not all CommandDedupKeys were declared as inputs"));
    }
    if (!inputList.containsAll(inputPackageKeys.values())) {
      throw new InvalidTransactionException(String.format("Not all PackageKeys were declared as inputs"));
    }
    if (!inputList.containsAll(inputContractKeys.values())) {
      throw new InvalidTransactionException(String.format("Not all ContractKeys were declared as inputs"));
    }

    // 3. Fetch all of the inputs
    // Add all of the contracts to a list of contracts
    // Add all of the templates/packages to a list of templates/packages

    Map<DamlStateKey, DamlStateValue> inputContracts = ledgerState.getDamlContracts(inputContractKeys.keySet());
    Map<DamlStateKey, DamlStateValue> inputCommandDedup = ledgerState
        .getDamlCommandDedups(inputCommandDedupKeys.keySet());
    Map<DamlStateKey, DamlStateValue> inputPackages = ledgerState.getDamlPackages(inputPackageKeys.keySet());

    Map<DamlStateKey, DamlStateValue> stateMap = new HashMap<>();
    stateMap.putAll(inputContracts);
    stateMap.putAll(inputCommandDedup);
    stateMap.putAll(inputPackages);
    return stateMap;
  }

  private Configuration getConfiguration() {
    // TODO this needs to be replaced with a proper configuration source
    Configuration config = new Configuration(
        new TimeModel(Duration.ofMinutes(1), Duration.ofMinutes(1), Duration.ofMinutes(1)));
    return config;
  }

  @Override
  public Collection<String> getNameSpaces() {
    return Arrays.asList(new String[] {this.namespace});
  }

  private Timestamp getRecordTime() {
    // TODO This should be replaced with the time service
    return new Timestamp(System.currentTimeMillis());
  }

  @Override
  public String getVersion() {
    return this.version;
  }

  private void recordState(final LedgerState ledgerState, final DamlSubmission submission,
      final Map<DamlLogEntryId, DamlLogEntry> inputLogEntries, final Map<DamlStateKey, DamlStateValue> stateMap,
      final DamlLogEntryId entryId) throws InternalError, InvalidTransactionException {

    Tuple2<DamlLogEntry, Map<DamlStateKey, DamlStateValue>> processSubmission = this.committer
        .processSubmission(getConfiguration(), entryId, getRecordTime(), submission, inputLogEntries, stateMap);

    DamlLogEntry newLogEntry = processSubmission._1;
    ledgerState.setDamlLogEntry(entryId, newLogEntry);

    Map<DamlStateKey, DamlStateValue> newState = processSubmission._2;
    for (Entry<DamlStateKey, DamlStateValue> e : newState.entrySet()) {
      DamlStateKey k = e.getKey();
      DamlStateValue val = e.getValue();
      switch (k.getKeyCase()) {
      case COMMAND_DEDUP:
        ledgerState.setDamlCommandDedup(k.getCommandDedup(), val.getCommandDedup());
        break;
      case CONTRACT_ID:
        ledgerState.setDamlContract(k.getContractId(), val.getContractState());
        break;
      case PACKAGE_ID:
        ledgerState.setDamlPackage(k.getPackageId(), val.getArchive());
        break;
      default:
        break;
      }
    }
  }

  @Override
  public String transactionFamilyName() {
    return this.familyName;
  }

}
