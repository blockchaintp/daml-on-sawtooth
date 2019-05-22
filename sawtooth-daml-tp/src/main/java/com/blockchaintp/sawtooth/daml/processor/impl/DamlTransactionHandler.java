package com.blockchaintp.sawtooth.daml.processor.impl;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

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
import com.google.protobuf.util.Timestamps;

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

  private static final Logger LOGGER = Logger.getLogger(DamlTransactionHandler.class.getName());

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
    LOGGER.fine(String.format("Processing transaction %s", tpProcessRequest.getSignature()));
    basicRequestChecks(tpProcessRequest);

    LedgerState ledgerState = new DamlLedgerState(state);
    SawtoothDamlTransaction tx;
    TransactionHeader txHeader = tpProcessRequest.getHeader();
    DamlSubmission submission;
    DamlLogEntryId entryId;
    try {
      tx = SawtoothDamlTransaction.parseFrom(tpProcessRequest.getPayload());
      entryId = DamlLogEntryId.parseFrom(tx.getLogEntryId());
      submission = KeyValueSubmission.unpackDamlSubmission(tx.getSubmission());
    } catch (InvalidProtocolBufferException e) {
      InvalidTransactionException ite = new InvalidTransactionException(
          String.format("Payload is unparseable, and not a valid DamlSubmission", e.getMessage().getBytes()));
      ite.initCause(e);
      throw ite;
    }

    Map<DamlStateKey, DamlStateValue> stateMap = buildStateMap(ledgerState, txHeader, submission);

    Map<DamlLogEntryId, DamlLogEntry> inputLogEntries = buildLogEntryMap(ledgerState, txHeader, submission);

    recordState(ledgerState, submission, inputLogEntries, stateMap, entryId);
    LOGGER.fine(String.format("Finished processing transaction %s", tpProcessRequest.getSignature()));
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
    LOGGER.fine(String.format("Fetching DamlLog for this transaction"));

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
    LOGGER.fine(String.format("Fetching DamlState for this transaction"));
    Map<DamlStateKey, String> inputDamlStateKeys = KeyValueUtils.submissionToDamlStateAddress(submission);

    List<String> inputList = txHeader.getInputsList();
    if (!inputList.containsAll(inputDamlStateKeys.values())) {
      throw new InvalidTransactionException(String.format("Not all input DamlStateKeys were declared as inputs"));
    }

    Map<DamlStateKey, DamlStateValue> inputStates = ledgerState.getDamlStates(inputDamlStateKeys.keySet());
    return inputStates;
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

  private Timestamp getRecordTime(final LedgerState ledgerState) throws InternalError {
    com.google.protobuf.Timestamp recordTime = ledgerState.getRecordTime();
    long micros = Timestamps.toMicros(recordTime);
    return new Timestamp(micros);
  }

  @Override
  public String getVersion() {
    return this.version;
  }

  private void recordState(final LedgerState ledgerState, final DamlSubmission submission,
      final Map<DamlLogEntryId, DamlLogEntry> inputLogEntries, final Map<DamlStateKey, DamlStateValue> stateMap,
      final DamlLogEntryId entryId) throws InternalError, InvalidTransactionException {
    LOGGER.fine(String.format("Recording state at %s", entryId));

    Tuple2<DamlLogEntry, Map<DamlStateKey, DamlStateValue>> processSubmission = this.committer.processSubmission(
        getConfiguration(), entryId, getRecordTime(ledgerState), submission, inputLogEntries, stateMap);

    Map<DamlStateKey, DamlStateValue> newState = processSubmission._2;
    ledgerState.setDamlStates(newState.entrySet());

    DamlLogEntry newLogEntry = processSubmission._1;
    ledgerState.addDamlLogEntry(entryId, newLogEntry);
  }

  @Override
  public String transactionFamilyName() {
    return this.familyName;
  }

}
