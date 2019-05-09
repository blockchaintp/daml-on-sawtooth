package com.blockchaintp.sawtooth.timekeeper;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.blockchaintp.sawtooth.daml.rpc.exception.SawtoothWriteServiceException;
import com.blockchaintp.sawtooth.timekeeper.protobuf.TimeKeeperUpdate;
import com.blockchaintp.sawtooth.timekeeper.util.Namespace;
import com.blockchaintp.utils.KeyManager;
import com.blockchaintp.utils.SawtoothClientUtils;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Timestamp;

import sawtooth.sdk.messaging.Future;
import sawtooth.sdk.messaging.Stream;
import sawtooth.sdk.processor.exceptions.ValidatorConnectionError;
import sawtooth.sdk.protobuf.Batch;
import sawtooth.sdk.protobuf.ClientBatchSubmitRequest;
import sawtooth.sdk.protobuf.ClientBatchSubmitResponse;
import sawtooth.sdk.protobuf.Message;
import sawtooth.sdk.protobuf.Transaction;

/**
 * TimeKeeperRunnable is designed to be run in a fixed schedule thread pool,
 * where it will periodically submit a TimeKeeperUpdate.
 */
public final class TimeKeeperRunnable implements Runnable {

  private static final Logger LOGGER = LoggerFactory.getLogger(TimeKeeperRunnable.class);

  private final Stream stream;
  private final KeyManager keyManager;
  private final String recordAddress;

  /**
   * Main constructor.
   * @param kmgr A key manager implementation which will provide a keys for the transactions,
   * @param dedicatedStream a stream which is not shared among any other threads.
   */
  public TimeKeeperRunnable(final KeyManager kmgr, final Stream dedicatedStream) {
    this.keyManager = kmgr;
    this.stream = dedicatedStream;
    this.recordAddress = Namespace.makeAddress(Namespace.getNameSpace(), this.keyManager.getPublicKeyInHex());
  }

  @Override
  public void run() {
    Clock clock = Clock.systemUTC();
    Instant instant = clock.instant();
    Timestamp ts = Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build();
    TimeKeeperUpdate update = TimeKeeperUpdate.newBuilder().setTimeUpdate(ts).build();

    List<String> inputAddresses = Arrays.asList(this.recordAddress, Namespace.TIMEKEEPER_GLOBAL_RECORD);
    List<String> outputAddresses = Arrays.asList(this.recordAddress, Namespace.TIMEKEEPER_GLOBAL_RECORD);

    Transaction updateTransaction = SawtoothClientUtils.makeSawtoothTransaction(this.keyManager, inputAddresses,
        outputAddresses, Arrays.asList(), update.toByteString());

    Batch batch = SawtoothClientUtils.makeSawtoothBatch(this.keyManager, Arrays.asList(updateTransaction));

    try {
      sendBatch(batch);
    } catch (SawtoothWriteServiceException exc) {
      LOGGER.warn("Error updating TimeKeeper records", exc);
    }
  }

  private void sendBatch(final Batch batch) throws SawtoothWriteServiceException {
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
