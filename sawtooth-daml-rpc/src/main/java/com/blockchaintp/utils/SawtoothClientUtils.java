package com.blockchaintp.utils;

import java.security.SecureRandom;
import java.util.Collection;

import com.blockchaintp.sawtooth.daml.util.Namespace;
import com.google.protobuf.ByteString;

import sawtooth.sdk.protobuf.Batch;
import sawtooth.sdk.protobuf.BatchHeader;
import sawtooth.sdk.protobuf.BatchHeader.Builder;
import sawtooth.sdk.protobuf.Transaction;
import sawtooth.sdk.protobuf.TransactionHeader;

/**
 * Utility methods and constants useful for interacting with Sawtooth as a
 * client.
 */
public final class SawtoothClientUtils {

  private SawtoothClientUtils() {
  }

  /**
   * Create a batch. Transaction passed are specified to be processed in order.
   * @param keyManager A key manager handling the identity to be used for signing
   *                   etc.
   * @param txns       a collection of transactions
   * @return the assembled batch
   */
  public static final Batch makeSawtoothBatch(final KeyManager keyManager, final Collection<Transaction> txns) {
    BatchHeader.Builder batchHeaderBldr = BatchHeader.newBuilder().setSignerPublicKey(keyManager.getPublicKeyInHex());
    for (Transaction tx : txns) {
      batchHeaderBldr.addTransactionIds(tx.getHeaderSignature());
    }
    BatchHeader batchHeader = batchHeaderBldr.build();
    String signedHeader = keyManager.sign(batchHeader.toByteArray());
    return Batch.newBuilder().setHeader(batchHeader.toByteString()).setHeaderSignature(signedHeader)
        .addAllTransactions(txns).build();
  }

  /**
   * Make a sawtooth transaction based on the provided parameters.
   * @param keyManager              a keymanager to provide the necessary identity
   *                                info
   * @param inputAddresses          state addresses required for input to this
   *                                transaction
   * @param outputAddresses         state addresses this transaction will output
   *                                to
   * @param dependentTransactionIds transaction ids this transaction is dependent
   *                                upon
   * @param payload                 the ByteString payload of this transaction
   * @return the transaction
   */
  public static final Transaction makeSawtoothTransaction(final KeyManager keyManager,
      final Collection<String> inputAddresses, final Collection<String> outputAddresses,
      final Collection<String> dependentTransactionIds, final ByteString payload) {
  
    String payloadSha256 = Namespace.getHash(payload.toString());
    TransactionHeader.Builder txnHeaderBldr = TransactionHeader.newBuilder().setFamilyName(Namespace.getNameSpace())
        .setFamilyVersion(Namespace.DAML_FAMILY_VERSION_1_0).setSignerPublicKey(keyManager.getPublicKeyInHex())
        .setNonce(SawtoothClientUtils.generateNonce()).setPayloadSha512(payloadSha256).addAllInputs(inputAddresses)
        .addAllOutputs(outputAddresses).addAllDependencies(dependentTransactionIds);
    TransactionHeader txnHeader = txnHeaderBldr.build();
  
    String signedHeader = keyManager.sign(txnHeader.toByteArray());
    return Transaction.newBuilder().setHeader(txnHeader.toByteString()).setHeaderSignature(signedHeader)
        .setPayload(payload).build();
  }

  /**
   * Generate a random nonce.
   * @return the nonce
   */
  public static final String generateNonce() {
    SecureRandom secureRandom = new SecureRandom();
    final int seedByteCount = 20;
    byte[] seed = secureRandom.generateSeed(seedByteCount);
    return seed.toString();
  }
}
