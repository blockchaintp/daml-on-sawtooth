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

import static com.blockchaintp.sawtooth.timekeeper.util.Namespace.TIMEKEEPER_GLOBAL_RECORD;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import com.blockchaintp.sawtooth.daml.protobuf.SawtoothDamlTransaction;
import com.blockchaintp.sawtooth.daml.rpc.exception.SawtoothWriteServiceException;
import com.blockchaintp.sawtooth.daml.util.KeyValueUtils;
import com.blockchaintp.sawtooth.daml.util.Namespace;
import com.blockchaintp.utils.KeyManager;
import com.blockchaintp.utils.SawtoothClientUtils;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlCommandDedupKey;
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
import sawtooth.sdk.protobuf.ClientBatchSubmitResponse.Status;
import sawtooth.sdk.protobuf.Message;
import sawtooth.sdk.protobuf.Transaction;
import scala.Option;
import scala.collection.JavaConverters;

/**
 * A implementation of Sawtooth write service. This is responsible for writing
 * Daml submission to to Sawtooth validator.
 */
public final class SawtoothWriteService implements WriteService {

  private static final Logger LOGGER = Logger.getLogger(SawtoothWriteService.class.getName());

  private Stream stream;

  private KeyManager keyManager;

  private SawtoothTransactionsTracer sawtoothTransactionsTracer;

  private final String participantId;

  private ExecutorService watchThreadPool = Executors.newWorkStealingPool();

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
  public CompletionStage<PartyAllocationResult> allocateParty(final Option<String> hint,
      final Option<String> displayName) {
    // TODO Implement this, for now report unsupported
    return CompletableFuture.completedStage(new PartyAllocationResult.NotSupported$());
  }

  private String getParticipantId() {
    return this.participantId;
  }

  private String makeDamlCommandDedupKeyAddress(final SubmitterInfo submitterInfo) {
    DamlCommandDedupKey dedupKey = DamlCommandDedupKey.newBuilder().setApplicationId(submitterInfo.applicationId())
        .setCommandId(submitterInfo.commandId()).setSubmitter(submitterInfo.submitter()).build();
    DamlStateKey dedupStateKey = DamlStateKey.newBuilder().setCommandDedup(dedupKey).build();
    String dedupStateAddress = Namespace.makeAddressForType(dedupStateKey);
    return dedupStateAddress;
  }

  private List<String> makeInputAddresses(final DamlSubmission submission) {
    List<String> inputAddresses = new ArrayList<>();
    Map<DamlStateKey, String> submissionToDamlStateAddress = KeyValueUtils.submissionToDamlStateAddress(submission);
    inputAddresses.addAll(submissionToDamlStateAddress.values());
    inputAddresses.add(TIMEKEEPER_GLOBAL_RECORD);
    inputAddresses.add(Namespace.DAML_CONFIG_TIME_MODEL);

    Map<DamlLogEntryId, String> submissionToLogAddressMap = KeyValueUtils.submissionToLogAddressMap(submission);
    for (DamlLogEntryId e : submissionToLogAddressMap.keySet()) {
      String logAddresses = Namespace.makeAddressForType(e);
      inputAddresses.add(logAddresses);
    }
    inputAddresses.add(Namespace.DAML_LOG_ENTRY_LIST);
    return inputAddresses;
  }

  private Collection<String> makeOutputAddresses(final scala.collection.immutable.List<Archive> archives,
      final DamlLogEntryId damlLogEntryId) {
    Collection<Archive> archiveColl = JavaConverters.asJavaCollection(archives);
    ArrayList<String> addresses = new ArrayList<>();
    for (Archive arch : archiveColl) {
      String packageId = arch.getHash();
      DamlStateKey packageKey = DamlStateKey.newBuilder().setPackageId(packageId).build();
      String address = Namespace.makeAddressForType(packageKey);
      addresses.add(address);
    }
    String logEntryAddress = Namespace.makeAddressForType(damlLogEntryId);
    addresses.add(logEntryAddress);
    return addresses;
  }

  private List<String> makeOutputAddresses(
      final GenTransaction<NodeId, ContractId, VersionedValue<ContractId>> transaction,
      final DamlLogEntryId logEntryId) {
    scala.collection.immutable.List<DamlStateKey> transactionOutputs = KeyValueSubmission.transactionOutputs(logEntryId,
        transaction);
    Collection<DamlStateKey> damlStateKeys = JavaConverters.asJavaCollection(transactionOutputs);
    List<String> outputAddresses = new ArrayList<>();

    for (DamlStateKey dk : damlStateKeys) {
      String addr = Namespace.makeAddressForType(dk);
      outputAddresses.add(addr);
      LOGGER.fine(String.format("Adding output address %s for key %s", addr, dk));
    }
    outputAddresses.add(Namespace.makeAddressForType(logEntryId));
    outputAddresses.add(Namespace.DAML_LOG_ENTRY_LIST);
    return outputAddresses;
  }

  private SubmissionResult submissionToResponse(final Future submissionFuture) throws SawtoothWriteServiceException {
    try {
      ByteString result = submissionFuture.getResult();
      ClientBatchSubmitResponse getResponse = ClientBatchSubmitResponse.parseFrom(result);
      Status status = getResponse.getStatus();
      switch (status) {
      case OK:
        LOGGER.info(String.format("ClientBatchSubmit response is OK"));
        return new SubmissionResult.Acknowledged$();
      case QUEUE_FULL:
        LOGGER.info(String.format("ClientBatchSubmit response is QUEUE_FULL"));
        return new SubmissionResult.Overloaded$();
      default:
        LOGGER.info(String.format("ClientBatchSubmit response is %s", status.toString()));
        throw new SawtoothWriteServiceException(
            String.format("ClientBatchSubmit returned %s", getResponse.getStatus()));
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

  synchronized private CompletionStage<SubmissionResult> sendToValidator(final Collection<Batch> batches) {
    // Push to TraceTransaction class
    for (Batch b : batches) {
      this.sawtoothTransactionsTracer.putWriteTransactions(b.toString());
      LOGGER.info(String.format("Batch submission %s", b.getHeaderSignature()));
    }
    ClientBatchSubmitRequest cbsReq = ClientBatchSubmitRequest.newBuilder().addAllBatches(batches).build();
    Future streamToValidator = this.stream.send(Message.MessageType.CLIENT_BATCH_SUBMIT_REQUEST, cbsReq.toByteString());
    return CompletableFuture.supplyAsync(() -> {
      try {
        return submissionToResponse(streamToValidator);
      } catch (SawtoothWriteServiceException exc) {
        LOGGER.severe(exc.getMessage());
        return new SubmissionResult.Acknowledged$();
      }
    }, watchThreadPool);
  }

  private Batch submissionToBatch(final DamlSubmission submission, final Collection<String> inputAddresses,
      final Collection<String> outputAddresses, final DamlLogEntryId damlLogEntryId) {
    SawtoothDamlTransaction payload = SawtoothDamlTransaction.newBuilder()
        .setSubmission(KeyValueSubmission.packDamlSubmission(submission))
        .setLogEntryId(KeyValueCommitting.packDamlLogEntryId(damlLogEntryId)).build();

    Transaction sawtoothTxn = SawtoothClientUtils.makeSawtoothTransaction(this.keyManager, Namespace.DAML_FAMILY_NAME,
        Namespace.DAML_FAMILY_VERSION_1_0, inputAddresses, outputAddresses, Arrays.asList(), payload.toByteString());
    Batch sawtoothBatch = SawtoothClientUtils.makeSawtoothBatch(this.keyManager, Arrays.asList(sawtoothTxn));
    LOGGER.fine(
        String.format("Batch %s has tx %s", sawtoothBatch.getHeaderSignature(), sawtoothTxn.getHeaderSignature()));
    return sawtoothBatch;
  }

  @Override
  public CompletionStage<SubmissionResult> submitTransaction(final SubmitterInfo submitterInfo,
      final TransactionMeta transactionMeta,
      final GenTransaction<NodeId, ContractId, VersionedValue<ContractId>> transaction) {

    LOGGER.info(String.format("Max Record Time = %s", submitterInfo.maxRecordTime()));
    DamlSubmission submission = KeyValueSubmission.transactionToSubmission(submitterInfo, transactionMeta, transaction);
    DamlLogEntryId damlLogEntryId = DamlLogEntryId.newBuilder()
        .setEntryId(ByteString.copyFromUtf8(UUID.randomUUID().toString())).build();

    List<String> outputAddresses = makeOutputAddresses(transaction, damlLogEntryId);

    List<String> inputAddresses = makeInputAddresses(submission);

    // Have to add dedupStateKey since that is missed in transactionOutputs
    String dedupStateAddress = makeDamlCommandDedupKeyAddress(submitterInfo);
    if (!outputAddresses.contains(dedupStateAddress)) {
      LOGGER.warning(String.format("Output addresses do not contain the dedup key, adding addr=%s", dedupStateAddress));
      outputAddresses.add(dedupStateAddress);
    }
    if (!inputAddresses.contains(dedupStateAddress)) {
      LOGGER.warning(String.format("Input addresses do not contain the dedup key, adding addr=%s", dedupStateAddress));
      inputAddresses.add(dedupStateAddress);
    }

    // Have to add all the input address to output addresses since
    // some are missed on the KeyValueSubmission.transactionOutputs
    for (String addr : inputAddresses) {
      if (!outputAddresses.contains(addr)) {
        LOGGER.warning(String.format("Output addresses do not contain an input key, adding addr=%s", addr));
        outputAddresses.add(addr);
      }
    }

    Batch sawtoothBatch = submissionToBatch(submission, inputAddresses, outputAddresses, damlLogEntryId);

    return sendToValidator(List.of(sawtoothBatch));
  }

  @Override
  public CompletionStage<SubmissionResult> uploadPublicPackages(final scala.collection.immutable.List<Archive> archives,
      final String sourceDescription) {
    DamlSubmission submission = KeyValueSubmission.archivesToSubmission(archives, sourceDescription,
        this.getParticipantId());

    DamlLogEntryId damlLogEntryId = DamlLogEntryId.newBuilder()
        .setEntryId(ByteString.copyFromUtf8(UUID.randomUUID().toString())).build();
    Collection<String> outputAddresses = makeOutputAddresses(archives, damlLogEntryId);

    Collection<String> inputAddresses = makeInputAddresses(submission);

    // Have to add all the input address to output addresses since
    // some are missed on the KeyValueSubmission.transactionOutputs
    for (String addr : inputAddresses) {
      if (!outputAddresses.contains(addr)) {
        LOGGER.warning(String.format("Output addresses do not contain an input key, adding addr=%s", addr));
        outputAddresses.add(addr);
      }
    }

    Batch sawtoothBatch = submissionToBatch(submission, inputAddresses, outputAddresses, damlLogEntryId);

    return sendToValidator(List.of(sawtoothBatch));
  }

}
