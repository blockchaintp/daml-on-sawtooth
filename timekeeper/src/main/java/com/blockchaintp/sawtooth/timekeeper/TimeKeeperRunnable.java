/* Copyright 2019 Blockchain Technology Partners
 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at
     http://www.apache.org/licenses/LICENSE-2.0
 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
------------------------------------------------------------------------------*/
package com.blockchaintp.sawtooth.timekeeper;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.blockchaintp.sawtooth.timekeeper.exceptions.TimeKeeperException;
import com.blockchaintp.sawtooth.timekeeper.protobuf.TimeKeeperUpdate;
import com.blockchaintp.sawtooth.timekeeper.util.Namespace;
import com.blockchaintp.utils.KeyManager;
import com.blockchaintp.utils.SawtoothClientUtils;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;

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

  private final KeyManager keyManager;
  private final String recordAddress;

  private final Stream stream;

  /**
   * Main constructor.
   * @param kmgr      A key manager implementation which will provide a keys for
   *                  the transactions,
   * @param argStream the stream connecting to the validator.
   */
  public TimeKeeperRunnable(final KeyManager kmgr, final Stream argStream) {
    this.keyManager = kmgr;
    this.stream = argStream;
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
    Transaction updateTransaction = SawtoothClientUtils.makeSawtoothTransaction(this.keyManager,
        Namespace.TIMEKEEPER_FAMILY_NAME, Namespace.TIMEKEEPER_FAMILY_VERSION_1_0, inputAddresses, outputAddresses,
        Arrays.asList(), update.toByteString());

    Batch batch = SawtoothClientUtils.makeSawtoothBatch(this.keyManager, Arrays.asList(updateTransaction));

    try {
      LOGGER.info("Sending a participant time update {} time={}", this.keyManager.getPublicKeyInHex(),
          new Date(Timestamps.toMillis(ts)));
      sendBatch(batch);
    } catch (TimeKeeperException exc) {
      LOGGER.warn("Error updating TimeKeeper records", exc);
    }
  }

  private void sendBatch(final Batch batch) throws TimeKeeperException {
    ClientBatchSubmitRequest cbsReq = ClientBatchSubmitRequest.newBuilder().addBatches(batch).build();
    Future streamToValidator = this.stream.send(Message.MessageType.CLIENT_BATCH_SUBMIT_REQUEST, cbsReq.toByteString());
    ClientBatchSubmitResponse submitResponse = null;
    try {
      ByteString result = streamToValidator.getResult();
      submitResponse = ClientBatchSubmitResponse.parseFrom(result);
      LOGGER.debug("Batch submitted {}", batch.getHeaderSignature());
      if (submitResponse.getStatus() != ClientBatchSubmitResponse.Status.OK) {
        LOGGER.warn("Batch submit response resulted in error: {}", submitResponse.getStatus());
        throw new TimeKeeperException(
            String.format("Batch submit response resulted in error: %s", submitResponse.getStatus()));
      }
    } catch (InterruptedException e) {
      TimeKeeperException tke = new TimeKeeperException(
          String.format("Sawtooth validator interrupts exception. Details: %s", e.getMessage()));
      tke.initCause(e);
      throw tke;
    } catch (ValidatorConnectionError e) {
      TimeKeeperException tke = new TimeKeeperException(
          String.format("Sawtooth validator connection error. Details: %s", e.getMessage()));
      tke.initCause(e);
      throw tke;
    } catch (InvalidProtocolBufferException e) {
      TimeKeeperException tke = new TimeKeeperException(
          String.format("Invalid protocol buffer exception. Details: %s", e.getMessage()));
      tke.initCause(e);
      throw tke;
    }
  }

}
