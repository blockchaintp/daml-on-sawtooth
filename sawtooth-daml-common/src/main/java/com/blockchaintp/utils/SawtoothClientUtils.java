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
import java.io.UnsupportedEncodingException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import com.blockchaintp.sawtooth.daml.protobuf.VersionedEnvelope;
import com.blockchaintp.sawtooth.daml.protobuf.VersionedEnvelope.Builder;
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
    BatchHeader batchHeader = batchHeaderBldr.build();
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
    final int seedByteCount = 20;
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

    byte[] nonceBytes = new byte[seedByteCount];
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
    try {
      byte[] data = arg.getBytes("UTF-8");
      return getHash(data);
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException("Charset UTF-8 is not found! This should never happen.", e);
    }
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

  public static ByteString wrap(final ByteString value) throws InternalError {
    ByteString v =
        VersionedEnvelope.newBuilder().setData(compressByteString(value))
          .setHasMore(false)
          .build().toByteString();
    return v;
  }

  public static List<ByteString> wrapMultipart(final ByteString value, final int maxPartSize)
      throws InternalError {
    ByteString compressedData = compressByteString(value);
    if (compressedData.size() < maxPartSize) {
      return List.of(wrap(value));
    }

    List<ByteString> retList = new ArrayList<>();
    int i = 0;
    byte[] splitBytes = compressedData.toByteArray();
    while (i < splitBytes.length) {
      byte[] part = Arrays.copyOfRange(splitBytes, i, Math.min(i + maxPartSize, splitBytes.length));
      i = i + maxPartSize;
      Builder leaf = VersionedEnvelope.newBuilder().setData(ByteString.copyFrom(part));
      if (i < splitBytes.length) {
        leaf.setHasMore(true);
      }
      retList.add(leaf.build().toByteString());
    }
    return retList;
  }

  public static ByteString unwrapMultipart(final List<VersionedEnvelope> veList) throws InternalError {
    ByteString data = ByteString.EMPTY;
    for (VersionedEnvelope e : veList) {
      data = data.concat(e.getData());
    }
    ByteString uData = uncompressByteString(data);
    return uData;
  }

  public static ByteString unwrap(final ByteString wrappedVal) throws InternalError {
    VersionedEnvelope envelope;
    try {
      envelope = VersionedEnvelope.parseFrom(wrappedVal);
      switch (envelope.getVersion()) {
        case "":
        case "1":
          ByteString v = uncompressByteString(envelope.getData());
          return v;
        default:
          LOGGER.error("Envelope specified an unknown version: " + envelope.getVersion());
          throw new InternalError(
              "Envelope specified an unknown version: " + envelope.getVersion());
      }
    } catch (final InvalidProtocolBufferException e) {
      LOGGER.error("Error deserializing value " + e.getMessage());
      throw new InternalError(e.getMessage());
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
      final byte[] buffer = new byte[COMPRESS_BUFFER_SIZE];
      while (!deflater.finished()) {
        final int bCount = deflater.deflate(buffer);
        baos.write(buffer, 0, bCount);
      }
      deflater.end();

      final ByteString bs = ByteString.copyFrom(baos.toByteArray());
      final long compressStop = System.currentTimeMillis();
      final long compressTime = compressStop - compressStart;
      LOGGER.debug("Compressed ByteString time={}, original_size={}, new_size={}",
          compressTime, inputBytes.length, baos.size());
      return bs;
    } catch (final IOException exc) {
      LOGGER.error("ByteArrayOutputStream.close() has thrown an error which should never happen!");
      throw new InternalError(exc.getMessage());
    }
  }

  private static ByteString uncompressByteString(final ByteString compressedInput)
      throws InternalError {
    final long uncompressStart = System.currentTimeMillis();
    if (compressedInput.size() == 0) {
      return ByteString.EMPTY;
    }
    final Inflater inflater = new Inflater();
    final byte[] inputBytes = compressedInput.toByteArray();
    inflater.setInput(inputBytes);

    try (ByteArrayOutputStream baos = new ByteArrayOutputStream(inputBytes.length)) {
      final byte[] buffer = new byte[COMPRESS_BUFFER_SIZE];
      try {
        while (!inflater.finished()) {
          final int bCount = inflater.inflate(buffer);
          baos.write(buffer, 0, bCount);
        }
        inflater.end();

        final ByteString bs = ByteString.copyFrom(baos.toByteArray());
        final long uncompressStop = System.currentTimeMillis();
        final long uncompressTime = uncompressStop - uncompressStart;
        LOGGER.debug("Uncompressed ByteString time={}, original_size={}, new_size={}",
            uncompressTime, inputBytes.length, baos.size());
        return bs;
      } catch (final DataFormatException exc) {
        LOGGER.error("Error uncompressing stream, throwing InternalError! {}",
            exc.getMessage());
        throw new InternalError(exc.getMessage());
      }
    } catch (final IOException exc) {
      LOGGER.error("ByteArrayOutputStream.close() has thrown an error which should never happen!");
      throw new InternalError(exc.getMessage());
    }

  }

  public static Future submitBatch(final Batch batch, final Stream stream) {
    final ClientBatchSubmitRequest cbsReq =
        ClientBatchSubmitRequest.newBuilder().addBatches(batch).build();
    return stream.send(Message.MessageType.CLIENT_BATCH_SUBMIT_REQUEST, cbsReq.toByteString());
  }
}
