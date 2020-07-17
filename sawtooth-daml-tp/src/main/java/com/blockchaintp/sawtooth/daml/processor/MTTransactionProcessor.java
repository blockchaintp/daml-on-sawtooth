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
package com.blockchaintp.sawtooth.daml.processor;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.blockchaintp.sawtooth.daml.messaging.ZmqStream;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import sawtooth.sdk.messaging.Future;
import sawtooth.sdk.processor.Context;
import sawtooth.sdk.processor.StreamContext;
import sawtooth.sdk.processor.TransactionHandler;
import sawtooth.sdk.processor.exceptions.InternalError;
import sawtooth.sdk.processor.exceptions.InvalidTransactionException;
import sawtooth.sdk.processor.exceptions.ValidatorConnectionError;
import sawtooth.sdk.protobuf.Message;
import sawtooth.sdk.protobuf.PingResponse;
import sawtooth.sdk.protobuf.TpProcessRequest;
import sawtooth.sdk.protobuf.TpProcessResponse;
import sawtooth.sdk.protobuf.TpRegisterRequest;

/**
 * A multithreaded Sawtooth transaction processor.
 */
public class MTTransactionProcessor implements Runnable {

  private static final int LOG_METRICS_INTERVAL = 1000;

  private static final Logger LOGGER = LoggerFactory.getLogger(MTTransactionProcessor.class);

  private TransactionHandler handler;

  private BlockingQueue<Map.Entry<String, TpProcessResponse>> outQueue;

  private ZmqStream stream;

  private ExecutorService executor;

  /**
   * Constructs a MTTransactionProcessor utilizing the given transaction handler.
   * NOTE: The TransactionHandler.apply() method must be thread-safe.
   *
   * @param txHandler The handler to apply to this processor
   * @param address   the address of the ZMQ stream
   */
  public MTTransactionProcessor(final TransactionHandler txHandler, final String address) {
    this.handler = txHandler;
    this.outQueue = new LinkedBlockingQueue<>();
    this.stream = new ZmqStream(address);
    this.executor = Executors.newWorkStealingPool();
  }

  @Override
  public final void run() {
    boolean stopped = false;
    this.register();
    long outStandingTx = 0;
    long enqueueCount = 0;
    long dequeueCount = 0;
    while (!stopped) {
      Message inMessage = this.stream.receiveNoException(1);
      while (inMessage != null) {
        enqueueCount += handleInbound(inMessage);
        Map.Entry<String, TpProcessResponse> outPair = outQueue.poll();
        dequeueCount += handleOutbound(outPair);
        inMessage = this.stream.receiveNoException(1);
      }
      Map.Entry<String, TpProcessResponse> outPair = outQueue.poll();
      while (outPair != null) {
        dequeueCount += handleOutbound(outPair);
        outPair = outQueue.poll();
      }
      outStandingTx = enqueueCount - dequeueCount;
      if (enqueueCount % LOG_METRICS_INTERVAL == 0) {
        LOGGER.info(String.format("Enqueued %s transactions, Dequeued %s responses, outStanding tx=%s", enqueueCount,
            dequeueCount, outStandingTx));
      }
    }

  }

  private int handleOutbound(final Map.Entry<String, TpProcessResponse> outPair) {
    if (outPair != null) {
      this.stream.sendBack(Message.MessageType.TP_PROCESS_REQUEST, outPair.getKey(), outPair.getValue().toByteString());
      return 1;
    }
    return 0;
  }

  private int handleInbound(final Message inMessage) {
    if (inMessage.getMessageType() == Message.MessageType.PING_REQUEST) {
      LOGGER.debug("Recieved Ping Message.");
      PingResponse pingResponse = PingResponse.newBuilder().build();
      this.stream.sendBack(Message.MessageType.PING_RESPONSE, inMessage.getCorrelationId(),
          pingResponse.toByteString());
      return 0;
    } else {
      Runnable processMessage = new ProcessRunnable(inMessage, this.handler, this.outQueue);
      this.executor.submit(processMessage);
      return 1;
    }
  }

  private void register() {
    LOGGER.info("Registering TP");
    boolean registered = false;
    while (!registered) {
      try {
        TpRegisterRequest registerRequest = TpRegisterRequest.newBuilder()
            .setFamily(this.handler.transactionFamilyName()).addAllNamespaces(this.handler.getNameSpaces())
            .setVersion(this.handler.getVersion()).setMaxOccupancy(Runtime.getRuntime().availableProcessors()).build();
        Future fut = this.stream.send(Message.MessageType.TP_REGISTER_REQUEST, registerRequest.toByteString());
        fut.getResult();
        registered = true;
      } catch (InterruptedException | ValidatorConnectionError e) {
        LOGGER.warn("Failed to register with validator, retrying...", e);
      }
    }
  }

  /**
   * A Runnable which processes a single Message.
   */
  private final class ProcessRunnable implements Runnable {

    private Message message;
    private BlockingQueue<Entry<String, TpProcessResponse>> responses;
    private TransactionHandler handler;

    ProcessRunnable(final Message m, final TransactionHandler txHandler,
        final BlockingQueue<Map.Entry<String, TpProcessResponse>> responseQueue) {
      this.message = m;
      this.handler = txHandler;
      this.responses = responseQueue;
    }

    @Override
    public void run() {
      try {
        TpProcessRequest transactionRequest = TpProcessRequest.parseFrom(this.message.getContent());
        Context state = new StreamContext(stream, transactionRequest.getContextId());

        TpProcessResponse.Builder builder = TpProcessResponse.newBuilder();
        try {
          handler.apply(transactionRequest, state);
          builder.setStatus(TpProcessResponse.Status.OK);
        } catch (InvalidTransactionException ite) {
          LOGGER.warn("Invalid Transaction: " + ite.toString(), ite);
          builder.setStatus(TpProcessResponse.Status.INVALID_TRANSACTION);
          builder.setMessage(ite.getMessage());
          if (ite.getExtendedData() != null) {
            builder.setExtendedData(ByteString.copyFrom(ite.getExtendedData()));
          }
        } catch (InternalError ie) {
          LOGGER.warn("State Exception!: " + ie.toString(), ie);
          builder.setStatus(TpProcessResponse.Status.INTERNAL_ERROR);
          builder.setMessage(ie.getMessage());
          if (ie.getExtendedData() != null) {
            builder.setExtendedData(ByteString.copyFrom(ie.getExtendedData()));
          }
        } catch (Throwable t) {
          LOGGER.warn("Unknown Exception!: " + t.toString(), t);
          builder.setStatus(TpProcessResponse.Status.INTERNAL_ERROR);
          builder.setMessage(t.getMessage());
        }
        responses.put(Map.entry(message.getCorrelationId(), builder.build()));
      } catch (InvalidProtocolBufferException e) {
        LOGGER.warn("InvalidProtocolBufferException!: " + e.toString(), e);
      } catch (InterruptedException e) {
        LOGGER.warn("Interrupted while queueing a response!: " + e.toString(), e);
      }
    }

  }

}
