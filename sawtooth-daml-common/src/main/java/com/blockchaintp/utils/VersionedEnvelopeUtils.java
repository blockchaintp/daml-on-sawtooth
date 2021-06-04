
package com.blockchaintp.utils;

import static sawtooth.sdk.processor.Utils.hash512;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.blockchaintp.utils.protobuf.VersionedEnvelope;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.Arrays;

public class VersionedEnvelopeUtils {

  private static final int COMPRESS_BUFFER_SIZE = 1024;

  private static final Logger LOGGER = LoggerFactory.getLogger(VersionedEnvelopeUtils.class);

  private VersionedEnvelopeUtils() {}

    /**
   * Wrap the given ByteString with a VersionEnvelope and return.
   *
   * @param value The value to be wrapped
   * @return a ByteString representatin of the VersionedEnvelope
   * @throws InternalError for any serious error
   */
  public static ByteString wrap(final ByteString value) throws InternalError {
    var data = compressByteString(value);
    String contentHash = hash512(data.toByteArray());
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
    var compressedData = compressByteString(value);
    String contentHash = hash512(compressedData.toByteArray());
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
    String assembledHash = hash512(accumulatedBytes);
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
          String assembledHash = hash512(envelope.getData().toByteArray());
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
    final var deflater = new Deflater();
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

}
