package com.blockchaintp.sawtooth.daml.rpc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

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
import com.daml.ledger.participant.state.kvutils.KeyValueSubmission;
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
import scala.collection.JavaConverters;

/**
 * A implementation of Sawtooth write service. This is responsible for writing
 * Daml submission to to Sawtooth validator.
 */
public final class SawtoothWriteService implements WriteService {

  private static final Logger LOGGER = LoggerFactory.getLogger(SawtoothWriteService.class);

  private Stream stream;

  private KeyManager keyManager;

  private SawtoothTransactionsTracer sawtoothTransactionsTracer;

  /**
   * Construct a SawtoothWriteService instance from a concrete stream.
   * @param implementation of a ZMQ stream.
   * @param kmgr           the keyManager for this service.
   * @param txnTracer      a RESTFul interface to push record of sawtooth
   *                       transactions.
   */
  public SawtoothWriteService(final Stream implementation, final KeyManager kmgr,
      final SawtoothTransactionsTracer txnTracer) {
    this.stream = implementation;
    this.keyManager = kmgr;
    this.sawtoothTransactionsTracer = txnTracer;
  }

  /**
   * Constructor a SawtoothWriteService instance from an address.
   * @param validatorAddress in String format e.g. "http://localhost:3030".
   * @param kmgr             the keyManager for this service.
   * @param txnTracer        a RESTFul interface to push record of sawtooth
   *                         transactions.
   */
  public SawtoothWriteService(final String validatorAddress, final KeyManager kmgr,
      final SawtoothTransactionsTracer txnTracer) {
    this(new ZmqStream(validatorAddress), kmgr, txnTracer);
  }

  @Override
  public void submitTransaction(final SubmitterInfo submitterInfo, final TransactionMeta transactionMeta,
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
    }
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
        .setSubmission(transactionToSubmission.toByteString()).setLogEntryId(damlLogEntryId.toByteString()).build();

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

  /**
   * Create and submit a sawtooth transaction which uploads the provided archive.
   * @param archive the archive to upload.
   */
  public void uploadArchive(final Archive archive) {
    DamlSubmission archiveSubmission = KeyValueSubmission.archiveToSubmission(archive);
    String packageId = archive.getHash();

    DamlStateKey packageKey = DamlStateKey.newBuilder().setPackageId(packageId).build();
    String packageAddress = Namespace.makeAddressForType(packageKey);
    DamlLogEntryId damlLogEntryId = DamlLogEntryId.newBuilder()
        .setEntryId(ByteString.copyFromUtf8(UUID.randomUUID().toString())).build();
    String logEntryAddress = Namespace.makeAddressForType(damlLogEntryId);

    Collection<String> outputAddresses = new ArrayList<>();
    outputAddresses.add(packageAddress);
    outputAddresses.add(logEntryAddress);
    outputAddresses.add(Namespace.DAML_LOG_ENTRY_LIST);

    SawtoothDamlTransaction payload = SawtoothDamlTransaction.newBuilder()
        .setSubmission(archiveSubmission.toByteString()).setLogEntryId(damlLogEntryId.toByteString()).build();

    Transaction sawtoothTxn = SawtoothClientUtils.makeSawtoothTransaction(this.keyManager, Namespace.DAML_FAMILY_NAME,
        Namespace.DAML_FAMILY_VERSION_1_0, Collections.emptyList(), outputAddresses, Arrays.asList(),
        payload.toByteString());
    Batch sawtoothBatch = SawtoothClientUtils.makeSawtoothBatch(this.keyManager, Arrays.asList(sawtoothTxn));

    // Push to TraceTransaction class
    this.sawtoothTransactionsTracer.putWriteTransactions(sawtoothBatch.toString());

    try {
      sendToValidator(sawtoothBatch);
    } catch (SawtoothWriteServiceException e) {
      LOGGER.error(e.getMessage());
    }
  }
}
