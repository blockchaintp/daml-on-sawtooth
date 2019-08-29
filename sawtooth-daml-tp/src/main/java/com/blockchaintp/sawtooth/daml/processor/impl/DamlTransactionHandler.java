/* Copyright 2019 Blockchain Technology Partners
 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at
     http://www.apache.org/licenses/LICENSE-2.0
 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
------------------------------------------------------------------------------*/
package com.blockchaintp.sawtooth.daml.processor.impl;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.blockchaintp.sawtooth.daml.processor.DamlCommitter;
import com.blockchaintp.sawtooth.daml.processor.LedgerState;
import com.blockchaintp.sawtooth.daml.protobuf.SawtoothDamlOperation;
import com.blockchaintp.sawtooth.daml.protobuf.SawtoothDamlParty;
import com.blockchaintp.sawtooth.daml.protobuf.SawtoothDamlTransaction;
import com.blockchaintp.sawtooth.daml.util.KeyValueUtils;
import com.blockchaintp.sawtooth.daml.util.Namespace;
import com.daml.ledger.participant.state.backport.TimeModel;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntry;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntryId;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlStateKey;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlStateValue;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlSubmission;
import com.daml.ledger.participant.state.kvutils.KeyValueCommitting;
import com.daml.ledger.participant.state.kvutils.KeyValueSubmission;
import com.daml.ledger.participant.state.v1.Configuration;
import com.digitalasset.daml.lf.data.Time.Timestamp;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.Timestamps;

import sawtooth.sdk.processor.Context;
import sawtooth.sdk.processor.TransactionHandler;
import sawtooth.sdk.processor.exceptions.InternalError;
import sawtooth.sdk.processor.exceptions.InvalidTransactionException;
import sawtooth.sdk.protobuf.TpProcessRequest;
import sawtooth.sdk.protobuf.TransactionHeader;
import scala.Option;
import scala.Tuple2;

/**
 * A TransactionHandler implementation which handles DAML.
 *
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
   *
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
    LOGGER.info(String.format("Processing transaction %s", tpProcessRequest.getSignature()));
    basicRequestChecks(tpProcessRequest);

    LedgerState ledgerState = new DamlLedgerState(state);
    TransactionHeader txHeader = tpProcessRequest.getHeader();
    try {
      SawtoothDamlOperation operation = SawtoothDamlOperation.parseFrom(tpProcessRequest.getPayload());
      if (operation.hasTransaction()) {
        SawtoothDamlTransaction tx = operation.getTransaction();
        DamlLogEntryId entryId = KeyValueCommitting.unpackDamlLogEntryId(tx.getLogEntryId());
        DamlSubmission submission = KeyValueSubmission.unpackDamlSubmission(tx.getSubmission());
        processTransaction(ledgerState, txHeader, submission, entryId);
      }
    } catch (InvalidProtocolBufferException e) {
      InvalidTransactionException ite = new InvalidTransactionException(
          String.format("Payload is unparseable, and not a valid DamlSubmission", e.getMessage().getBytes()));
      ite.initCause(e);
      throw ite;
    }
  }

  private void processTransaction(final LedgerState ledgerState, final TransactionHeader txHeader,
      final DamlSubmission submission, final DamlLogEntryId entryId) throws InternalError, InvalidTransactionException {
    long fetchStateStart = System.currentTimeMillis();
    Map<DamlStateKey, Option<DamlStateValue>> stateMap = buildStateMap(ledgerState, txHeader, submission);

    List<String> currentLogEntryList = ledgerState.getLogEntryIndex();
    long recordStateStart = System.currentTimeMillis();
    recordState(ledgerState, submission, stateMap, entryId, currentLogEntryList);

    long processFinished = System.currentTimeMillis();
    long recordStateTime = processFinished - recordStateStart;
    long fetchStateTime = recordStateStart - fetchStateStart;
    LOGGER.info(String.format("Finished processing transaction %s times=[fetch=%s,record=%s]",
        txHeader.getPayloadSha512(), fetchStateTime, recordStateTime));
  }

  /**
   * Fundamental checks of the transaction.
   *
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

  private Map<DamlStateKey, Option<DamlStateValue>> buildStateMap(final LedgerState ledgerState,
      final TransactionHeader txHeader, final DamlSubmission submission)
      throws InvalidTransactionException, InternalError {
    LOGGER.fine(String.format("Fetching DamlState for this transaction"));
    Map<DamlStateKey, String> inputDamlStateKeys = KeyValueUtils.submissionToDamlStateAddress(submission);

    List<String> inputList = txHeader.getInputsList();
    if (!inputList.containsAll(inputDamlStateKeys.values())) {
      throw new InvalidTransactionException(String.format("Not all input DamlStateKeys were declared as inputs"));
    }
    if (!inputList.contains(com.blockchaintp.sawtooth.timekeeper.util.Namespace.TIMEKEEPER_GLOBAL_RECORD)) {
      throw new InvalidTransactionException(String.format("TIMEKEEPER_GLOBAL_RECORD not declared as input"));
    }
    if (!inputList.contains(Namespace.DAML_LOG_ENTRY_LIST)) {
      throw new InvalidTransactionException(String.format("DAML_LOG_ENTRY_LIST not declared as input"));
    }
    Map<DamlStateKey, DamlStateValue> inputStates = ledgerState.getDamlStates(inputDamlStateKeys.keySet());

    Map<DamlStateKey, Option<DamlStateValue>> inputStatesWithOption = new HashMap<>();
    for (DamlStateKey k : inputDamlStateKeys.keySet()) {
      if (inputStates.containsKey(k)) {
        LOGGER.fine(
            String.format("Fetched %s(%s), address=%s", k, k.getKeyCase().toString(), Namespace.makeAddressForType(k)));
        Option<DamlStateValue> option = Option.apply(inputStates.get(k));
        if (inputStates.get(k).toByteString().size() == 0) {
          LOGGER.fine(String.format("Fetched %s(%s), address=%s, size=empty", k, k.getKeyCase().toString(),
              Namespace.makeAddressForType(k)));
        } else {
          LOGGER.fine(String.format("Fetched %s(%s), address=%s, size=%s", k, k.getKeyCase().toString(),
              Namespace.makeAddressForType(k), inputStates.get(k).toByteString().size()));
        }
        inputStatesWithOption.put(k, option);
      } else {
        LOGGER.fine(String.format("Fetched %s(%s), address=%s, size=empty", k, k.getKeyCase().toString(),
            Namespace.makeAddressForType(k)));
        inputStatesWithOption.put(k, Option.empty());
      }
    }
    return inputStatesWithOption;
  }

  private Configuration getConfiguration(final LedgerState ledgerState)
      throws InternalError, InvalidTransactionException {
    TimeModel tm = ledgerState.getTimeModel();
    if (tm == null) {
      LOGGER.info("No time model set on chain using defaults");
      tm = new TimeModel(Duration.ofSeconds(1), Duration.ofMinutes(2), Duration.ofMinutes(2));
    }
    LOGGER.fine(String.format("TimeModel set to %s", tm));
    Configuration config = new Configuration(tm);
    return config;
  }

  @Override
  public Collection<String> getNameSpaces() {
    return Arrays.asList(new String[] { this.namespace });
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
      final Map<DamlStateKey, Option<DamlStateValue>> stateMap, final DamlLogEntryId entryId,
      final List<String> currentLogEntryList) throws InternalError, InvalidTransactionException {

    long processStart = System.currentTimeMillis();
    Tuple2<DamlLogEntry, Map<DamlStateKey, DamlStateValue>> processSubmission = this.committer
        .processSubmission(getConfiguration(ledgerState), entryId, getRecordTime(ledgerState), submission, stateMap);
    long recordStart = System.currentTimeMillis();
    Map<DamlStateKey, DamlStateValue> newState = processSubmission._2;
    ledgerState.setDamlStates(newState.entrySet());

    DamlLogEntry newLogEntry = processSubmission._1;
    LOGGER.fine(String.format("Recording log at %s, address=%s", entryId, Namespace.makeAddressForType(entryId),
        newLogEntry.toByteString().size()));
    List<String> newLogEntryList = ledgerState.addDamlLogEntry(entryId, newLogEntry, currentLogEntryList);
    ledgerState.updateLogEntryIndex(newLogEntryList);
    long recordFinish = System.currentTimeMillis();
    long processTime = recordStart - processStart;
    long setStateTime = recordFinish - recordStart;
    long totalTime = recordFinish - processStart;
    LOGGER.info(String.format("Record state timings [ total=%s, process=%s, setState=%s ]", totalTime, processTime,
        setStateTime));
  }

  @Override
  public String transactionFamilyName() {
    return this.familyName;
  }

}
