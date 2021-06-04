/*
 * Copyright 2019 Blockchain Technology Partners Licensed under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable
 * law or agreed to in writing, software distributed under the License is distributed on an "AS IS"
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under the License.
 * ------------------------------------------------------------------------------
 */
package com.blockchaintp.sawtooth;

import static org.bitcoinj.core.Utils.HEX;
import static sawtooth.sdk.processor.Utils.hash512;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Collection;

import com.blockchaintp.keymanager.KeyManager;
import com.blockchaintp.utils.VersionedEnvelopeUtils;
import com.google.protobuf.ByteString;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sawtooth.sdk.messaging.Future;
import sawtooth.sdk.messaging.Stream;
import sawtooth.sdk.protobuf.Batch;
import sawtooth.sdk.protobuf.BatchHeader;
import sawtooth.sdk.protobuf.ClientBatchSubmitRequest;
import sawtooth.sdk.protobuf.Message;
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
   * A SecureRandom for use in creating batches.
   */
  private static SecureRandom secureRandom;
  private static int randomBytesGenerated;

  private static final Logger LOGGER = LoggerFactory.getLogger(SawtoothClientUtils.class);

  private static final int MAX_BYTES_PER_SEED = 10 * 1024 * 1024;

  /**
   * Create a batch. Transaction passed are specified to be processed in order.
   *
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
    var batchHeader = batchHeaderBldr.build();
    String signedHeader = keyManager.sign(batchHeader.toByteArray());
    return Batch.newBuilder().setHeader(batchHeader.toByteString()).setHeaderSignature(signedHeader)
        .addAllTransactions(txns).build();
  }

  /**
   * Make a sawtooth transaction based on the provided parameters.
   *
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
    ByteString wrappedPayload = VersionedEnvelopeUtils.wrap(payload);
    String payloadHash = hash512(wrappedPayload.toByteArray());
    TransactionHeader.Builder txnHeaderBldr = TransactionHeader.newBuilder().setFamilyName(familyName)
        .setFamilyVersion(familyVersion).clearBatcherPublicKey().setBatcherPublicKey(keyManager.getPublicKeyInHex())
        .setNonce(SawtoothClientUtils.generateNonce()).setPayloadSha512(payloadHash).addAllInputs(inputAddresses)
        .addAllOutputs(outputAddresses).addAllDependencies(dependentTransactionIds)
        .setSignerPublicKey(keyManager.getPublicKeyInHex());
    TransactionHeader txnHeader = txnHeaderBldr.build();

    String signedHeader = keyManager.sign(txnHeader.toByteArray());
    return Transaction.newBuilder().setHeader(txnHeader.toByteString()).setHeaderSignature(signedHeader)
        .setPayload(wrappedPayload).build();
  }

  /**
   * Generate a random nonce.
   *
   * @return the nonce
   */
  public static String generateNonce() {
    final var seedByteCount = 20;
    synchronized (SawtoothClientUtils.class) {
      if (null == secureRandom) {
        LOGGER.debug("Generating nonce - acquiring SecureRandom");
        secureRandom = new SecureRandom();
        randomBytesGenerated = -1;
      }
      if (randomBytesGenerated == -1) {
        LOGGER.debug("Generating nonce - regenerating seed");
        secureRandom.setSeed(secureRandom.generateSeed(seedByteCount));
        randomBytesGenerated = seedByteCount;
      } else {
        randomBytesGenerated += seedByteCount;
      }
      if (randomBytesGenerated > MAX_BYTES_PER_SEED) {
        randomBytesGenerated = -1;
      }
    }

    var nonceBytes = new byte[seedByteCount];
    LOGGER.debug("Generating nonce - generating nonce");
    secureRandom.nextBytes(nonceBytes);
    LOGGER.debug("Generating nonce - nonce generated");
    return HEX.encode(nonceBytes);
  }

  /**
   * For a given string return its sha512, transform the encoding problems into
   * RuntimeErrors.
   *
   * @param arg a string
   * @return the hash512 of the string
   */
  public static String getHash(final String arg) {
    byte[] data = arg.getBytes(StandardCharsets.UTF_8);
    return hash512(data);
  }

  /**
   * Subit the Batch to the sawtooth Stream.
   *
   * @param batch  the batch to submit
   * @param stream the stream to submit to
   * @return a Future upon which to wait
   */
  public static Future submitBatch(final Batch batch, final Stream stream) {
    final ClientBatchSubmitRequest cbsReq = ClientBatchSubmitRequest.newBuilder().addBatches(batch).build();
    return stream.send(Message.MessageType.CLIENT_BATCH_SUBMIT_REQUEST, cbsReq.toByteString());
  }
}
