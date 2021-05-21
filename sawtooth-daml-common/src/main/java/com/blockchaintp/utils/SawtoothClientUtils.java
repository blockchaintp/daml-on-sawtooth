/*
 * Copyright 2019 Blockchain Technology Partners Licensed under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable
 * law or agreed to in writing, software distributed under the License is distributed on an "AS IS"
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under the License.
 * ------------------------------------------------------------------------------
 */
package com.blockchaintp.utils;

import static org.bitcoinj.core.Utils.HEX;
import static sawtooth.sdk.processor.Utils.hash512;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import com.blockchaintp.sawtooth.daml.protobuf.VersionedEnvelope;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.Arrays;

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

  private static final int COMPRESS_BUFFER_SIZE = 1024;

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
    ByteString wrappedPayload = SawtoothClientUtils.wrap(payload);
    String payloadHash = getHash(wrappedPayload);
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
    return getHash(data);
  }

  /**
   * For a given ByteString return its sha512.
   *
   * @param arg a ByteString
   * @return the hash512 of the ByteString
   */
  public static String getHash(final ByteString arg) {
    return getHash(arg.toByteArray());
  }

  /**
   * For a given array of bytes return its sha512.
   *
   * @param arg the bytes
   * @return the hash512 of the byte array
   */
  public static String getHash(final byte[] arg) {
    return hash512(arg);
  }

  /**
   * Wrap the given ByteString with a VersionEnvelope and return.
   *
   * @param value The value to be wrapped
   * @return a ByteString representatin of the VersionedEnvelope
   * @throws InternalError for any serious error
   */
  public static ByteString wrap(final ByteString value) throws InternalError {
    ByteString data = compressByteString(value);
    String contentHash = getHash(data.toByteArray());
    return VersionedEnvelope.newBuilder().setData(data).setParts(1).setPartNumber(0).setContentHash(contentHash).build()
        .toByteString();
  }

  private static List<byte[]> splitBytes(final byte[] value, final int maxPartSize) {
    List<byte[]> chunks = new ArrayList<>();
    for (var i = 0; i < value.length; i += maxPartSize) {
      byte[] chunk = Arrays.copyOfRange(value, i, Math.min(i + maxPartSize, value.length));
      chunks.add(chunk);
    }
    return chunks;
  }

  /**
   * Wrap the the given list of byte arrays into a sequence of VersionedEnvelopes.
   * The content must match the providedHash
   *
   * @param vals        a List of byte arrays to wrap
   * @param contentHash the hash of the total original data
   * @return a List of VersionedEnvelope
   */
  private static List<VersionedEnvelope> wrapList(final List<byte[]> vals, final String contentHash) {
    List<VersionedEnvelope> envelopes = new ArrayList<>();
    var index = 0;
    for (byte[] val : vals) {
      VersionedEnvelope env = VersionedEnvelope.newBuilder().setContentHash(contentHash)
          .setData(ByteString.copyFrom(val)).setParts(vals.size()).setPartNumber(index).build();
      envelopes.add(env);
      index++;
    }
    return envelopes;
  }

  /**
   * Wrap a ByteString into a List of VersionedEnvelopes of at most maxPartsSize
   * length. The list is returned as a list of ByteStrings and is compressed.
   *
   * @param value       the value, usually large to be wrapped
   * @param maxPartSize the maximum size of a given part
   * @return a list of ByteString
   * @throws InternalError a serious parse error
   */
  public static List<ByteString> wrapMultipart(final ByteString value, final int maxPartSize) throws InternalError {
    ByteString compressedData = compressByteString(value);
    String contentHash = getHash(compressedData.toByteArray());
    List<ByteString> retList = new ArrayList<>();
    byte[] compressedBytes = compressedData.toByteArray();
    List<byte[]> splitBytes = splitBytes(compressedBytes, maxPartSize);
    List<VersionedEnvelope> envelopes = wrapList(splitBytes, contentHash);
    for (VersionedEnvelope e : envelopes) {
      retList.add(e.toByteString());
    }
    return retList;
  }

  /**
   * Unrwap the list of VersionedEnvelopes provided into the original ByteString.
   *
   * @param veList the list of VersionedEnvelopes
   * @return the original ByteString
   * @throws InternalError a serious error has occurred
   */
  public static ByteString unwrapMultipart(final List<VersionedEnvelope> veList) throws InternalError {
    String contentHash = null;
    var accumulatedBytes = new byte[] {};
    for (VersionedEnvelope e : veList) {
      if (contentHash == null) {
        contentHash = e.getContentHash();
      }
      accumulatedBytes = Arrays.concatenate(accumulatedBytes, e.getData().toByteArray());
    }
    var data = ByteString.copyFrom(accumulatedBytes);
    String assembledHash = getHash(accumulatedBytes);
    if (assembledHash.equals(contentHash)) {
      LOGGER.trace("Assembled hash looks good {} = {}", contentHash, assembledHash);
    } else {
      LOGGER.warn("Assembled hash does not match! {} != {}", contentHash, assembledHash);
    }

    return uncompressByteString(data);
  }

  /**
   * Unwrap the ByteString of a given VersionedEnvelope into the original
   * ByteString.
   *
   * @param wrappedVal the versioned envelope
   * @return the original ByteString
   * @throws InternalError a parse error
   */
  public static ByteString unwrap(final ByteString wrappedVal) throws InternalError {
    VersionedEnvelope envelope;
    try {
      envelope = VersionedEnvelope.parseFrom(wrappedVal);
      switch (envelope.getVersion()) {
        case "":
        case "1":
          String assembledHash = getHash(envelope.getData().toByteArray());
          String contentHash = envelope.getContentHash();
          if (contentHash.equals(assembledHash)) {
            LOGGER.trace("Assembled hash looks good {} = {}", contentHash, assembledHash);
          } else {
            LOGGER.warn("Assembled hash does not match! {} != {}", contentHash, assembledHash);
          }
          ByteString v = uncompressByteString(envelope.getData());
          return v;
        default:
          LOGGER.warn("Envelope specified an unknown version: {}", envelope.getVersion());
          throw new InternalError("Envelope specified an unknown version: " + envelope.getVersion());
      }
    } catch (final InvalidProtocolBufferException e) {
      throw new InternalError("Error deserializing value: " + e.getMessage(), e);
    }
  }

  private static ByteString compressByteString(final ByteString input) throws InternalError {
    final long compressStart = System.currentTimeMillis();
    if (input.size() == 0) {
      return ByteString.EMPTY;
    }
    final Deflater deflater = new Deflater();
    deflater.setLevel(Deflater.BEST_SPEED);
    final byte[] inputBytes = input.toByteArray();

    deflater.setInput(inputBytes);
    deflater.finish();

    try (ByteArrayOutputStream baos = new ByteArrayOutputStream(inputBytes.length);) {
      final var buffer = new byte[COMPRESS_BUFFER_SIZE];
      while (!deflater.finished()) {
        final int bCount = deflater.deflate(buffer);
        baos.write(buffer, 0, bCount);
      }
      deflater.end();

      final var bs = ByteString.copyFrom(baos.toByteArray());
      final long compressStop = System.currentTimeMillis();
      final long compressTime = compressStop - compressStart;
      LOGGER.trace("Compressed ByteString time={}, original_size={}, new_size={}", compressTime, inputBytes.length,
          baos.size());
      return bs;
    } catch (final IOException exc) {
      throw new InternalError("ByteArrayOutputStream.close() has thrown an error which should never happen!", exc);
    }
  }

  private static ByteString uncompressByteString(final ByteString compressedInput) throws InternalError {
    final long uncompressStart = System.currentTimeMillis();
    if (compressedInput.size() == 0) {
      return ByteString.EMPTY;
    }
    final var inflater = new Inflater();
    final byte[] inputBytes = compressedInput.toByteArray();
    inflater.setInput(inputBytes);

    try (var baos = new ByteArrayOutputStream(inputBytes.length)) {
      final var buffer = new byte[COMPRESS_BUFFER_SIZE];
      LOGGER.trace("Uncompressing ByteString original_size={}", inputBytes.length);
      while (!inflater.finished()) {
        final int bCount = inflater.inflate(buffer);
        baos.write(buffer, 0, bCount);
      }
      inflater.end();

      final var bs = ByteString.copyFrom(baos.toByteArray());
      final long uncompressStop = System.currentTimeMillis();
      final long uncompressTime = uncompressStop - uncompressStart;
      LOGGER.trace("Uncompressed ByteString time={}, original_size={}, new_size={}", uncompressTime, inputBytes.length,
          baos.size());
      return bs;
    } catch (final DataFormatException exc) {
      throw new InternalError("Error uncompressing stream! ", exc);
    } catch (final IOException exc) {
      throw new InternalError("ByteArrayOutputStream.close() has thrown an error which should never happen!", exc);
    }
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
