package com.blockchaintp.sawtooth.daml.rpc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import sawtooth.sdk.messaging.Future;
import sawtooth.sdk.messaging.Stream;
import sawtooth.sdk.messaging.ZmqStream;
import sawtooth.sdk.processor.exceptions.ValidatorConnectionError;
import sawtooth.sdk.protobuf.Batch;
import sawtooth.sdk.protobuf.ClientBatchGetResponse;
import sawtooth.sdk.protobuf.Message;
import sawtooth.sdk.protobuf.Transaction;
import scala.collection.JavaConverters;

/**
 * A implementation of Sawtooth write service. This is responsible for writing
 * Daml submission to to Sawtooth validator.
 */
public class SawtoothWriteService implements WriteService {

  private static final Logger LOGGER = LoggerFactory.getLogger(SawtoothWriteService.class);

  private Stream stream;

  private KeyManager keyManager;

  /**
   * Construct a SawtoothWriteService instance from a concrete stream.
   * @param implementation of a ZMQ stream.
   * @param kmgr the keyManager for this service
   */
  public SawtoothWriteService(final Stream implementation, final KeyManager kmgr) {
    this.stream = implementation;
    this.keyManager = kmgr;
  }

  /**
   * Constructor a SawtoothWriteService instance from an address.
   * @param validatorAddress in String format e.g. "http://localhost:3030".
   * @param kmgr the keyManager for this service
   */
  public SawtoothWriteService(final String validatorAddress, final KeyManager kmgr) {
    this(new ZmqStream(validatorAddress), kmgr);
  }

  @Override
  public final void submitTransaction(final SubmitterInfo submitterInfo, final TransactionMeta transactionMeta,
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

    Map<DamlStateKey, String> submissionToDamlStateAddress = KeyValueUtils
        .submissionToDamlStateAddress(transactionToSubmission);
    inputAddresses.addAll(submissionToDamlStateAddress.values());

    Map<DamlLogEntryId, String> submissionToLogAddressMap = KeyValueUtils
        .submissionToLogAddressMap(transactionToSubmission);
    inputAddresses.addAll(submissionToLogAddressMap.values());

    SawtoothDamlTransaction payload = SawtoothDamlTransaction.newBuilder()
        .setSubmission(transactionToSubmission.toByteString()).setLogEntryId(damlLogEntryId.toByteString()).build();

    Transaction sawtoothTxn = SawtoothClientUtils.makeSawtoothTransaction(this.keyManager, inputAddresses,
        outputAddresses, Arrays.asList(), payload.toByteString());
    Batch sawtoothBatch = SawtoothClientUtils.makeSawtoothBatch(this.keyManager, Arrays.asList(sawtoothTxn));

    try {
      sendToValidator(sawtoothBatch);
    } catch (SawtoothWriteServiceException e) {
      LOGGER.error(e.getMessage());
    }
  }

  private void sendToValidator(final Batch sawtootBatch) throws SawtoothWriteServiceException {
    Future streamToValidator = this.stream.send(Message.MessageType.CLIENT_BATCH_GET_REQUEST,
        sawtootBatch.toByteString());
    ClientBatchGetResponse getResponse = null;
    try {
      ByteString result = streamToValidator.getResult();
      getResponse = ClientBatchGetResponse.parseFrom(result);
      if (getResponse.getStatus() != ClientBatchGetResponse.Status.OK) {
        throw new SawtoothWriteServiceException();
      }

    } catch (InterruptedException e) {
      throw new SawtoothWriteServiceException(
          String.format("Sawtooth validator interrupts exception. Details: %s", e.getMessage()));
    } catch (ValidatorConnectionError e) {
      throw new SawtoothWriteServiceException(
          String.format("Sawtooth validator connection error. Details: %s", e.getMessage()));
    } catch (InvalidProtocolBufferException e) {
      throw new SawtoothWriteServiceException(
          String.format("Invalid protocol buffer exception. Details: %s", e.getMessage()));
    } finally {
      try {
        stream.close();
      } catch (Exception e) {
        throw new SawtoothWriteServiceException(
            String.format("Unable to close writer stream exception. Details: %s", e.getMessage()));
      }
    }
  }

}
