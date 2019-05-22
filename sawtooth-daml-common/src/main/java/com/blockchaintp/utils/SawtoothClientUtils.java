package com.blockchaintp.utils;

import java.io.UnsupportedEncodingException;
import java.security.SecureRandom;
import java.util.Collection;

import com.google.protobuf.ByteString;

import sawtooth.sdk.processor.Utils;
import sawtooth.sdk.protobuf.Batch;
import sawtooth.sdk.protobuf.BatchHeader;
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
  public static Batch makeSawtoothBatch(final KeyManager keyManager, final Collection<Transaction> txns) {
    BatchHeader.Builder batchHeaderBldr = BatchHeader.newBuilder().clearSignerPublicKey()
        .setSignerPublicKey(keyManager.getPublicKeyInHex());
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
   * @param familyName              the family name
   * @param familyVersion           the family version
   * @param inputAddresses          state addresses required for input to this
   *                                transaction
   * @param outputAddresses         state addresses this transaction will output
   *                                to
   * @param dependentTransactionIds transaction ids this transaction is dependent
   *                                upon
   * @param payload                 the ByteString payload of this transaction
   * @return the transaction
   */
  public static Transaction makeSawtoothTransaction(final KeyManager keyManager, final String familyName,
      final String familyVersion, final Collection<String> inputAddresses, final Collection<String> outputAddresses,
      final Collection<String> dependentTransactionIds, final ByteString payload) {

    String payloadHash = getHash(payload);
    TransactionHeader.Builder txnHeaderBldr = TransactionHeader.newBuilder().setFamilyName(familyName)
        .setFamilyVersion(familyVersion).clearBatcherPublicKey().setBatcherPublicKey(keyManager.getPublicKeyInHex())
        .setNonce(SawtoothClientUtils.generateNonce()).setPayloadSha512(payloadHash).addAllInputs(inputAddresses)
        .addAllOutputs(outputAddresses).addAllDependencies(dependentTransactionIds)
        .setSignerPublicKey(keyManager.getPublicKeyInHex());
    TransactionHeader txnHeader = txnHeaderBldr.build();

    String signedHeader = keyManager.sign(txnHeader.toByteArray());
    return Transaction.newBuilder().setHeader(txnHeader.toByteString()).setHeaderSignature(signedHeader)
        .setPayload(payload).build();
  }

  /**
   * Generate a random nonce.
   * @return the nonce
   */
  public static String generateNonce() {
    SecureRandom secureRandom = new SecureRandom();
    final int seedByteCount = 20;
    byte[] seed = secureRandom.generateSeed(seedByteCount);
    return seed.toString();
  }

  /**
   * For a given string return its sha512, transform the encoding problems into
   * RuntimeErrors.
   * @param arg a string
   * @return the hash512 of the string
   */
  public static String getHash(final String arg) {
    try {
      byte[] data = arg.getBytes("UTF-8");
      return getHash(data);
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException("Charset UTF-8 is not found! This should never happen.", e);
    }
  }

  /**
   * For a given ByteString return its sha512.
   * @param arg a ByteString
   * @return the hash512 of the ByteString
   */
  public static String getHash(final ByteString arg) {
    return getHash(arg.toByteArray());
  }

  /**
   * For a given array of bytes return its sha512.
   * @param arg the bytes
   * @return the hash512 of the byte array
   */
  public static String getHash(final byte[] arg) {
    return Utils.hash512(arg);
  }
}
