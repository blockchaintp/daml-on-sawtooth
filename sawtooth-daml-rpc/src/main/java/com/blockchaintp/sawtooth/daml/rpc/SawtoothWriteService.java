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

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import com.blockchaintp.sawtooth.daml.protobuf.SawtoothDamlOperation;
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
import com.daml.ledger.participant.state.v1.UploadPackagesResult;
import com.daml.ledger.participant.state.v1.WriteService;
import com.digitalasset.daml.lf.transaction.GenTransaction;
import com.digitalasset.daml.lf.value.Value.ContractId;
import com.digitalasset.daml.lf.value.Value.NodeId;
import com.digitalasset.daml.lf.value.Value.VersionedValue;
import com.digitalasset.daml_lf.DamlLf.Archive;
import com.digitalasset.ledger.api.domain.PartyDetails;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import com.google.protobuf.util.JsonFormat.Printer;

import sawtooth.sdk.messaging.Future;
import sawtooth.sdk.messaging.Stream;
import sawtooth.sdk.messaging.ZmqStream;
import sawtooth.sdk.processor.exceptions.ValidatorConnectionError;
import sawtooth.sdk.protobuf.Batch;
import sawtooth.sdk.protobuf.ClientBatchStatus;
import sawtooth.sdk.protobuf.ClientBatchStatusRequest;
import sawtooth.sdk.protobuf.ClientBatchStatusResponse;
import sawtooth.sdk.protobuf.ClientBatchSubmitRequest;
import sawtooth.sdk.protobuf.ClientBatchSubmitResponse;
import sawtooth.sdk.protobuf.ClientBatchSubmitResponse.Status;
import sawtooth.sdk.protobuf.Message;
import sawtooth.sdk.protobuf.Message.MessageType;
import sawtooth.sdk.protobuf.Transaction;
import scala.Option;
import scala.collection.JavaConverters;

/**
 * A implementation of Sawtooth write service. This is responsible for writing
 * Daml submission to to Sawtooth validator.
 */
public final class SawtoothWriteService implements WriteService {

  private static final Logger LOGGER = Logger.getLogger(SawtoothWriteService.class.getName());

  private static final int DEFAULT_WAIT_TIME = 300;

  private Stream stream;

  private KeyManager keyManager;

  private SawtoothTransactionsTracer sawtoothTransactionsTracer;

  private final String participantId;

  private ExecutorService watchThreadPool = Executors.newWorkStealingPool();

  /**
   * Construct a SawtoothWriteService instance from a concrete stream.
   *
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
   *
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

    String submissionId = UUID.randomUUID().toString();
    DamlLogEntryId damlLogEntryId = DamlLogEntryId.newBuilder().setEntryId(ByteString.copyFromUtf8(submissionId))
        .build();

    DamlSubmission submission = KeyValueSubmission.partyToSubmission(submissionId, hint, displayName,
        getParticipantId());

    Collection<String> outputAddresses = makeOutputAddresses(submission, damlLogEntryId);
    Collection<String> inputAddresses = makeInputAddresses(submission);
    Collection<String> fixedAddresses = fixupAddresses(inputAddresses, outputAddresses);

    SawtoothDamlOperation operation = submissionToOperation(submission, damlLogEntryId);
    Batch batch = operationToBatch(operation, fixedAddresses, fixedAddresses);

    final PartyDetails details = new PartyDetails(hint.get(), displayName, false);
    Future fut = sendToValidator(batch);
    return waitForSubmitResponse(batch, fut).thenApplyAsync(x -> checkBatchWaitForTerminal(x), watchThreadPool)
        .<PartyAllocationResult>thenApply(result -> {
          switch (result.getValue()) {
          case COMMITTED:
            return new PartyAllocationResult.Ok(details);
          case INVALID:
            return new PartyAllocationResult.AlreadyExists$();
          case UNKNOWN:
          default:
            return new PartyAllocationResult.InvalidName(String.format("%s is not a valid party id", hint.get()));
          }
        });
  }

  /**
   * Return this participants identifier as a string.
   *
   * @return the id of the participant
   */
  public String getParticipantId() {
    return this.participantId;
  }

  private String makeDamlCommandDedupKeyAddress(final SubmitterInfo submitterInfo) {
    DamlCommandDedupKey dedupKey = DamlCommandDedupKey.newBuilder().setApplicationId(submitterInfo.applicationId())
        .setCommandId(submitterInfo.commandId()).setSubmitter(submitterInfo.submitter()).build();
    DamlStateKey dedupStateKey = DamlStateKey.newBuilder().setCommandDedup(dedupKey).build();
    String dedupStateAddress = Namespace.makeAddressForType(dedupStateKey);
    return dedupStateAddress;
  }

  private Collection<String> makeInputAddresses(final DamlSubmission submission) {
    Set<String> addresses = new HashSet<>();
    Map<DamlStateKey, String> submissionToDamlStateAddress = KeyValueUtils.submissionToDamlStateAddress(submission);
    addresses.addAll(submissionToDamlStateAddress.values());
    addresses.add(TIMEKEEPER_GLOBAL_RECORD);
    addresses.add(Namespace.DAML_CONFIG_TIME_MODEL);
    addresses.add(Namespace.DAML_LOG_ENTRY_LIST);
    return addresses;
  }

  private Collection<String> makeOutputAddresses(final DamlSubmission submission, final DamlLogEntryId logEntryId) {
    Collection<DamlStateKey> keys = JavaConverters
        .asJavaCollection(KeyValueCommitting.submissionOutputs(logEntryId, submission));
    Set<String> addresses = new HashSet<>();
    for (DamlStateKey k : keys) {
      addresses.add(Namespace.makeAddressForType(k));
    }
    addresses.add(Namespace.makeAddressForType(logEntryId));
    addresses.add(Namespace.DAML_LOG_ENTRY_LIST);
    return addresses;
  }

  private Map.Entry<String, ClientBatchSubmitResponse.Status> submissionToResponse(final String batchid,
      final Future submissionFuture) throws SawtoothWriteServiceException {
    try {
      ByteString result = submissionFuture.getResult();
      ClientBatchSubmitResponse getResponse = ClientBatchSubmitResponse.parseFrom(result);
      Status status = getResponse.getStatus();
      switch (status) {
      case OK:
        LOGGER.info(String.format("ClientBatchSubmit response is OK"));
        break;
      case QUEUE_FULL:
        LOGGER.info(String.format("ClientBatchSubmit response is QUEUE_FULL"));
        break;
      default:
        LOGGER.info(String.format("ClientBatchSubmit response is %s", status.toString()));
        throw new SawtoothWriteServiceException(
            String.format("ClientBatchSubmit returned %s", getResponse.getStatus()));
      }
      return Map.entry(batchid, status);
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

  private Future sendToValidator(final Batch batch) {
    // Push to TraceTransaction class

    try {
      Printer includingDefaultValueFields = JsonFormat.printer().preservingProtoFieldNames()
          .includingDefaultValueFields();
      String txnInJson = includingDefaultValueFields.print(batch);
      this.sawtoothTransactionsTracer.putWriteTransactions(txnInJson);
    } catch (Exception e) {
      // Can't do anything so not passing information to tracer
    }

    LOGGER.info(String.format("Batch submission %s", batch.getHeaderSignature()));
    ClientBatchSubmitRequest cbsReq = ClientBatchSubmitRequest.newBuilder().addBatches(batch).build();
    Future streamToValidator = this.stream.send(Message.MessageType.CLIENT_BATCH_SUBMIT_REQUEST, cbsReq.toByteString());
    return streamToValidator;
  }

  private synchronized CompletionStage<Map.Entry<String, ClientBatchSubmitResponse.Status>> waitForSubmitResponse(
      final Batch batch, final Future streamToValidator) {
    return CompletableFuture.supplyAsync(() -> {
      try {
        return submissionToResponse(batch.getHeaderSignature(), streamToValidator);
      } catch (SawtoothWriteServiceException exc) {
        LOGGER.severe(exc.getMessage());
        return Map.entry(batch.getHeaderSignature(), ClientBatchSubmitResponse.Status.INTERNAL_ERROR);
      }
    }, watchThreadPool);
  }

  private SubmissionResult batchSubmitToSubmissionResult(
      final Map.Entry<String, ClientBatchSubmitResponse.Status> submission) {
    switch (submission.getValue()) {
    case OK:
      return new SubmissionResult.Acknowledged$();
    case QUEUE_FULL:
      return new SubmissionResult.Overloaded$();
    default:
      return new SubmissionResult.Overloaded$();
    }
  }

  private UploadPackagesResult batchTerminalToUploadPackageResult(
      final Map.Entry<String, ClientBatchStatus.Status> submission) {
    switch (submission.getValue()) {
    case COMMITTED:
      return new UploadPackagesResult.Ok$();
    case INVALID:
      return new UploadPackagesResult.InvalidPackage(
          String.format("Package submission %s is invalid", submission.getKey()));
    default:
      return new UploadPackagesResult.InvalidPackage(
          String.format("Package submission %s has been lost", submission.getKey()));
    }
  }

  private Map.Entry<String, ClientBatchStatus.Status> checkBatchWaitForTerminal(
      final Map.Entry<String, ClientBatchSubmitResponse.Status> submission) {
    String batchid = submission.getKey();
    while (true) {
      try {
        ClientBatchStatusRequest cbstatReq = ClientBatchStatusRequest.newBuilder().addBatchIds(batchid).setWait(true)
            .setTimeout(DEFAULT_WAIT_TIME).build();
        Future cbstatFut = this.stream.send(MessageType.CLIENT_BATCH_STATUS_REQUEST, cbstatReq.toByteString());
        ByteString result = cbstatFut.getResult();
        ClientBatchStatusResponse cbsResp = ClientBatchStatusResponse.parseFrom(result);
        List<ClientBatchStatus> batchStatusesList = cbsResp.getBatchStatusesList();
        ClientBatchStatus.Status consolidatedStatus = ClientBatchStatus.Status.STATUS_UNSET;
        for (ClientBatchStatus thisStatus : batchStatusesList) {
          if (thisStatus.getStatus().compareTo(consolidatedStatus) > 0) {
            consolidatedStatus = thisStatus.getStatus();
          }
        }
        switch (consolidatedStatus) {
        case COMMITTED:
          return Map.entry(batchid, ClientBatchStatus.Status.COMMITTED);
        case INVALID:
          return Map.entry(batchid, ClientBatchStatus.Status.INVALID);
        case UNKNOWN:
          return Map.entry(batchid, ClientBatchStatus.Status.UNKNOWN);
        case PENDING:
        default:
        }
      } catch (InvalidProtocolBufferException | InterruptedException | ValidatorConnectionError e) {
        return Map.entry(batchid, ClientBatchStatus.Status.UNKNOWN);
      }
    }
  }

  private SawtoothDamlOperation submissionToOperation(final DamlSubmission submission,
      final DamlLogEntryId damlLogEntryId) {
    SawtoothDamlTransaction payload = SawtoothDamlTransaction.newBuilder()
        .setSubmission(KeyValueSubmission.packDamlSubmission(submission))
        .setLogEntryId(KeyValueCommitting.packDamlLogEntryId(damlLogEntryId)).build();
    return SawtoothDamlOperation.newBuilder().setTransaction(payload).build();
  }

  private Batch operationToBatch(final SawtoothDamlOperation operation, final Collection<String> inputAddresses,
      final Collection<String> outputAddresses) {
    Transaction sawtoothTxn = SawtoothClientUtils.makeSawtoothTransaction(this.keyManager, Namespace.DAML_FAMILY_NAME,
        Namespace.DAML_FAMILY_VERSION_1_0, inputAddresses, outputAddresses, Arrays.asList(), operation.toByteString());
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

    Collection<String> outputAddresses = makeOutputAddresses(submission, damlLogEntryId);
    Collection<String> inputAddresses = makeInputAddresses(submission);
    Collection<String> fixedAddresses = fixupAddresses(inputAddresses, outputAddresses);

    // Have to add dedupStateKey since that is missed in transactionOutputs
    String dedupStateAddress = makeDamlCommandDedupKeyAddress(submitterInfo);
    fixedAddresses.add(dedupStateAddress);

    SawtoothDamlOperation operation = submissionToOperation(submission, damlLogEntryId);
    Batch sawtoothBatch = operationToBatch(operation, fixedAddresses, fixedAddresses);
    Future validatorFuture = sendToValidator(sawtoothBatch);
    return waitForSubmitResponse(sawtoothBatch, validatorFuture).thenApply(x -> batchSubmitToSubmissionResult(x));
  }

  private Collection<String> fixupAddresses(final Collection<String> inputAddresses,
      final Collection<String> outputAddresses) {
    Set<String> unionSet = new HashSet<>();
    unionSet.addAll(inputAddresses);
    unionSet.addAll(outputAddresses);
    return unionSet;
  }

  @Override
  public CompletionStage<UploadPackagesResult> uploadPackages(final scala.collection.immutable.List<Archive> archives,
      final Option<String> optionalDescription) {
    String sourceDescription = "Uploaded package";
    if (optionalDescription.nonEmpty()) {
      sourceDescription = optionalDescription.get();
    }
    String submissionId = UUID.randomUUID().toString();
    DamlSubmission submission = KeyValueSubmission.archivesToSubmission(submissionId, archives, sourceDescription,
        this.getParticipantId());

    DamlLogEntryId damlLogEntryId = DamlLogEntryId.newBuilder().setEntryId(ByteString.copyFromUtf8(submissionId))
        .build();
    Collection<String> outputAddresses = makeOutputAddresses(submission, damlLogEntryId);
    Collection<String> inputAddresses = makeInputAddresses(submission);
    Collection<String> fixedAddresses = fixupAddresses(inputAddresses, outputAddresses);

    SawtoothDamlOperation operation = submissionToOperation(submission, damlLogEntryId);
    Batch sawtoothBatch = operationToBatch(operation, fixedAddresses, fixedAddresses);
    Future fut = sendToValidator(sawtoothBatch);
    return waitForSubmitResponse(sawtoothBatch, fut).thenApplyAsync(x -> checkBatchWaitForTerminal(x), watchThreadPool)
        .thenApply(x -> batchTerminalToUploadPackageResult(x));
  }
}
