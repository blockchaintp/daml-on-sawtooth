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
package com.blockchaintp.sawtooth.daml.rpc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.blockchaintp.sawtooth.daml.protobuf.SawtoothDamlTransaction;
import com.blockchaintp.sawtooth.daml.rpc.exception.SawtoothWriteServiceException;
import com.blockchaintp.sawtooth.daml.util.KeyValueUtils;
import com.blockchaintp.sawtooth.daml.util.Namespace;
import com.blockchaintp.utils.KeyManager;
import com.blockchaintp.utils.SawtoothClientUtils;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntryId;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlStateKey;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlSubmission;
import com.daml.ledger.participant.state.kvutils.KeyValueCommitting;
import com.daml.ledger.participant.state.kvutils.KeyValueSubmission;
import com.daml.ledger.participant.state.v1.PartyAllocationResult;
import com.daml.ledger.participant.state.v1.SubmissionResult;
import com.daml.ledger.participant.state.v1.SubmitterInfo;
import com.daml.ledger.participant.state.v1.TransactionMeta;
import com.daml.ledger.participant.state.v1.WriteService;
import com.digitalasset.daml.lf.transaction.GenTransaction;
import com.digitalasset.daml.lf.value.Value.ContractId;
import com.digitalasset.daml.lf.value.Value.NodeId;
import com.digitalasset.daml.lf.value.Value.VersionedValue;
import com.digitalasset.daml_lf.DamlLf.Archive;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import sawtooth.sdk.messaging.Future;
import sawtooth.sdk.messaging.Stream;
import sawtooth.sdk.messaging.ZmqStream;
import sawtooth.sdk.processor.exceptions.ValidatorConnectionError;
import sawtooth.sdk.protobuf.Batch;
import sawtooth.sdk.protobuf.ClientBatchSubmitRequest;
import sawtooth.sdk.protobuf.ClientBatchSubmitResponse;
import sawtooth.sdk.protobuf.Message;
import sawtooth.sdk.protobuf.Transaction;
import scala.Option;
import scala.collection.JavaConverters;
import scala.collection.immutable.List;

/**
 * A implementation of Sawtooth write service. This is responsible for writing
 * Daml submission to to Sawtooth validator.
 */
public final class SawtoothWriteService implements WriteService {

  private static final Logger LOGGER = LoggerFactory.getLogger(SawtoothWriteService.class);

  private Stream stream;

  private KeyManager keyManager;

  private SawtoothTransactionsTracer sawtoothTransactionsTracer;

  private final String participantId;

  /**
   * Construct a SawtoothWriteService instance from a concrete stream.
   * @param implementation of a ZMQ stream.
   * @param kmgr           the keyManager for this service.
   * @param txnTracer      a RESTFul interface to push record of sawtooth
   *                       transactions.
   * @param participant    a string identifying this participant
   */
  public SawtoothWriteService(final Stream implementation, final KeyManager kmgr,
      final SawtoothTransactionsTracer txnTracer, final String participant) {
    this.stream = implementation;
    this.keyManager = kmgr;
    this.sawtoothTransactionsTracer = txnTracer;
    this.participantId = participant;
  }

  /**
   * Constructor a SawtoothWriteService instance from an address.
   * @param validatorAddress in String format e.g. "http://localhost:3030".
   * @param kmgr             the keyManager for this service.
   * @param txnTracer        a RESTFul interface to push record of sawtooth
   *                         transactions.
   * @param participant      a string identifying this participant
   */
  public SawtoothWriteService(final String validatorAddress, final KeyManager kmgr,
      final SawtoothTransactionsTracer txnTracer, final String participant) {
    this(new ZmqStream(validatorAddress), kmgr, txnTracer, participant);
  }

  @Override
  public CompletionStage<SubmissionResult> submitTransaction(final SubmitterInfo submitterInfo,
      final TransactionMeta transactionMeta,
      final GenTransaction<NodeId, ContractId, VersionedValue<ContractId>> transaction) {

    DamlSubmission transactionToSubmission = KeyValueSubmission.transactionToSubmission(submitterInfo, transactionMeta,
        transaction);
    DamlLogEntryId damlLogEntryId = DamlLogEntryId.newBuilder()
        .setEntryId(ByteString.copyFromUtf8(UUID.randomUUID().toString())).build();
    scala.collection.immutable.List<DamlStateKey> transactionOutputs = KeyValueSubmission
        .transactionOutputs(damlLogEntryId, transaction);
    Collection<DamlStateKey> damlStateKeys = JavaConverters.asJavaCollection(transactionOutputs);

    Collection<String> outputAddresses = new ArrayList<>();
    Collection<String> inputAddresses = new ArrayList<>();

    for (DamlStateKey dk : damlStateKeys) {
      String addr = Namespace.makeAddressForType(dk);
      outputAddresses.add(addr);
      LOGGER.info(String.format("Adding output address %s for key %s", addr,dk));
    }
    outputAddresses.add(Namespace.makeAddressForType(damlLogEntryId));
    outputAddresses.add(Namespace.DAML_LOG_ENTRY_LIST);

    Map<DamlStateKey, String> submissionToDamlStateAddress = KeyValueUtils
        .submissionToDamlStateAddress(transactionToSubmission);
    inputAddresses.addAll(submissionToDamlStateAddress.values());
    inputAddresses.add(com.blockchaintp.sawtooth.timekeeper.util.Namespace.TIMEKEEPER_GLOBAL_RECORD);

    Map<DamlLogEntryId, String> submissionToLogAddressMap = KeyValueUtils
        .submissionToLogAddressMap(transactionToSubmission);
    inputAddresses.addAll(submissionToLogAddressMap.values());
    inputAddresses.add(Namespace.DAML_LOG_ENTRY_LIST);

    SawtoothDamlTransaction payload = SawtoothDamlTransaction.newBuilder()
        .setSubmission(KeyValueSubmission.packDamlSubmission(transactionToSubmission))
        .setLogEntryId(KeyValueCommitting.packDamlLogEntryId(damlLogEntryId)).build();

    Transaction sawtoothTxn = SawtoothClientUtils.makeSawtoothTransaction(this.keyManager, Namespace.DAML_FAMILY_NAME,
        Namespace.DAML_FAMILY_VERSION_1_0, inputAddresses, outputAddresses, Arrays.asList(), payload.toByteString());
    Batch sawtoothBatch = SawtoothClientUtils.makeSawtoothBatch(this.keyManager, Arrays.asList(sawtoothTxn));

    // Push to TraceTransaction class
    this.sawtoothTransactionsTracer.putWriteTransactions(sawtoothBatch.toString());

    try {
      sendToValidator(sawtoothBatch);
      CompletionStage<SubmissionResult> cs = CompletableFuture.completedStage(new SubmissionResult.Acknowledged$());
      return cs;
    } catch (SawtoothWriteServiceException e) {
      LOGGER.error(e.getMessage());
      CompletionStage<SubmissionResult> cs = CompletableFuture.completedStage(new SubmissionResult.Acknowledged$());
      return cs;
    }
  }

  private void sendToValidator(final Batch batch) throws SawtoothWriteServiceException {
    ClientBatchSubmitRequest cbsReq = ClientBatchSubmitRequest.newBuilder().addBatches(batch).build();
    Future streamToValidator = this.stream.send(Message.MessageType.CLIENT_BATCH_SUBMIT_REQUEST, cbsReq.toByteString());
    ClientBatchSubmitResponse getResponse = null;
    try {
      ByteString result = streamToValidator.getResult();
      getResponse = ClientBatchSubmitResponse.parseFrom(result);
      if (getResponse.getStatus() != ClientBatchSubmitResponse.Status.OK) {
        throw new SawtoothWriteServiceException();
      }

    } catch (InterruptedException e) {
      throw new SawtoothWriteServiceException(
          String.format("Sawtooth validator interrupts exception. Details: %s", e.getMessage()), e);
    } catch (ValidatorConnectionError e) {
      throw new SawtoothWriteServiceException(
          String.format("Sawtooth validator connection error. Details: %s", e.getMessage()), e);
    } catch (InvalidProtocolBufferException e) {
      throw new SawtoothWriteServiceException(
          String.format("Invalid protocol buffer exception. Details: %s", e.getMessage()), e);
    }
  }

  @Override
  public CompletionStage<PartyAllocationResult> allocateParty(final Option<String> hint,
      final Option<String> displayName) {
    // TODO Implement this, for now report unsupported
    return CompletableFuture.completedStage(new PartyAllocationResult.NotSupported$());
  }

  @Override
  public CompletionStage<SubmissionResult> uploadPublicPackages(final List<Archive> archives,
      final String sourceDescription) {
    DamlSubmission archiveSubmission = KeyValueSubmission.archivesToSubmission(archives, sourceDescription,
        this.getParticipantId());
    Collection<Archive> archiveColl = JavaConverters.asJavaCollection(archives);
    ArrayList<String> packageAddresses = new ArrayList<>();
    for (Archive arch : archiveColl) {
      String packageId = arch.getHash();
      DamlStateKey packageKey = DamlStateKey.newBuilder().setPackageId(packageId).build();
      String address = Namespace.makeAddressForType(packageKey);
      packageAddresses.add(address);
    }
    // String packageId = archive.getHash();
    DamlLogEntryId damlLogEntryId = DamlLogEntryId.newBuilder()
        .setEntryId(ByteString.copyFromUtf8(UUID.randomUUID().toString())).build();
    String logEntryAddress = Namespace.makeAddressForType(damlLogEntryId);

    Collection<String> inputAddresses = new ArrayList<>();
    inputAddresses.add(com.blockchaintp.sawtooth.timekeeper.util.Namespace.TIMEKEEPER_GLOBAL_RECORD);
    inputAddresses.add(Namespace.DAML_LOG_ENTRY_LIST);
    inputAddresses.addAll(packageAddresses);

    Collection<String> outputAddresses = new ArrayList<>();
    outputAddresses.addAll(packageAddresses);
    outputAddresses.add(logEntryAddress);
    outputAddresses.add(Namespace.DAML_LOG_ENTRY_LIST);

    SawtoothDamlTransaction payload = SawtoothDamlTransaction.newBuilder()
        .setSubmission(KeyValueSubmission.packDamlSubmission(archiveSubmission))
        .setLogEntryId(KeyValueCommitting.packDamlLogEntryId(damlLogEntryId)).build();

    Transaction sawtoothTxn = SawtoothClientUtils.makeSawtoothTransaction(this.keyManager, Namespace.DAML_FAMILY_NAME,
        Namespace.DAML_FAMILY_VERSION_1_0, inputAddresses, outputAddresses, Arrays.asList(), payload.toByteString());
    Batch sawtoothBatch = SawtoothClientUtils.makeSawtoothBatch(this.keyManager, Arrays.asList(sawtoothTxn));

    // Push to TraceTransaction class
    this.sawtoothTransactionsTracer.putWriteTransactions(sawtoothBatch.toString());

    try {
      sendToValidator(sawtoothBatch);
    } catch (SawtoothWriteServiceException e) {
      LOGGER.error(e.getMessage());
    }
    return CompletableFuture.completedFuture(new SubmissionResult.Acknowledged$());
  }

  private String getParticipantId() {
    return this.participantId;
  }

}
