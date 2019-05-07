package com.blockchaintp.sawtooth.daml.rpc;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import com.blockchaintp.sawtooth.daml.protobuf.SawtoothDamlTransaction;
import com.blockchaintp.sawtooth.daml.util.KeyValueUtils;
import com.blockchaintp.sawtooth.daml.util.Namespace;
import com.blockchaintp.utils.KeyManager;
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
import sawtooth.sdk.protobuf.Batch;
import sawtooth.sdk.protobuf.BatchHeader;
import sawtooth.sdk.protobuf.Message;
import sawtooth.sdk.protobuf.Transaction;
import sawtooth.sdk.protobuf.TransactionHeader;
import scala.collection.JavaConverters;
import sawtooth.sdk.protobuf.ClientBatchGetResponse;

/**
 * A implementation of Sawtooth writer service.
 *
 * @author paulwizviz
 *
 */
public class SawtoothWriteService implements WriteService {

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

    KeyManager km = KeyManager.createSECP256k1();

    Transaction sawtoothTxn = this.makeSawtoothTransaction(km, inputAddresses, outputAddresses, payload);
    Batch sawtootBatch = makeSawtoothBatch(km, sawtoothTxn);

    try {
      sendToValidator(sawtootBatch);
    } catch (SawtoothWriteServiceException e) {
      e.printStackTrace();
    }
  }

  private void sendToValidator(final Batch sawtootBatch) throws SawtoothWriteServiceException {
    String validatorAddress = ""; // TO=DO: find the correct address.
    Stream stream = new ZmqStream(validatorAddress);
    Future streamToValidator = stream.send(Message.MessageType.CLIENT_BATCH_GET_REQUEST, sawtootBatch.toByteString());
    ClientBatchGetResponse getResponse = null;
    try {
      ByteString result = streamToValidator.getResult();
      getResponse = ClientBatchGetResponse.parseFrom(result);
      if (getResponse.getStatus() != ClientBatchGetResponse.Status.OK) {
        throw new SawtoothWriteServiceException();
      }

    } catch (InterruptedException e) {
      throw new SawtoothWriteServiceException();
    } catch (InvalidProtocolBufferException e) {
      throw new SawtoothWriteServiceException();
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      try {
        stream.close();
      } catch (Exception e) {
        throw new SawtoothWriteServiceException();
      }
    }
  }

  private Transaction makeSawtoothTransaction(final KeyManager keyManager, final Collection<String> inputAddresses,
      final Collection<String> outputAddresses, final SawtoothDamlTransaction payload) {

    TransactionHeader txnHeader = TransactionHeader.newBuilder().setFamilyName(Namespace.getNameSpace())
        .setFamilyVersion(Namespace.DAML_FAMILY_VERSION_1_0).setSignerPublicKey(keyManager.getPublicKeyInHex())
        .setNonce(this.generateNonce()).setPayloadSha512Bytes(payload.getSubmission()).build();

    String signedHeader = keyManager.sign(txnHeader.toByteArray());
    return Transaction.newBuilder().setHeader(txnHeader.toByteString()).setHeaderSignature(signedHeader)
        .setPayload(payload.getSubmission()).build();
  }

  // This is based on the assumption there will only be one transaction per batch/
  private Batch makeSawtoothBatch(final KeyManager keyManager, final Transaction txn) {
    BatchHeader batchHeader = BatchHeader.newBuilder().setSignerPublicKey(keyManager.getPublicKeyInHex()).build();
    return Batch.newBuilder().setHeader(batchHeader.toByteString()).addTransactions(txn).build();
  }

  private String generateNonce() {
    SecureRandom secureRandom = new SecureRandom();
    final int seedByteCount = 20;
    byte[] seed = secureRandom.generateSeed(seedByteCount);
    return seed.toString();
  }

  /**
   *
   * @author paulwizviz
   *
   */
  private static class SawtoothWriteServiceException extends Exception {
  }

}
