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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.blockchaintp.sawtooth.daml.messaging.ZmqStream;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import sawtooth.sdk.messaging.Future;
import sawtooth.sdk.messaging.Stream;
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

  private static final int DEFAULT_MAX_THREADS = 10;

  private static final Logger LOGGER = Logger.getLogger(MTTransactionProcessor.class.getName());

  private TransactionHandler handler;

  private BlockingQueue<Map.Entry<String, TpProcessResponse>> outQueue;

  private Stream stream;

  private ExecutorService executor;

  /**
   * Constructs a MTTransactionProcessor utilizing the given transaction handler.
   * NOTE: The TransactionHandler.apply() method must be thread-safe.
   * @param txHandler The handler to apply to this processor
   * @param address   the address of the ZMQ stream
   */
  public MTTransactionProcessor(final TransactionHandler txHandler, final String address) {
    this.handler = txHandler;
    this.outQueue = new LinkedBlockingQueue<>();
    this.stream = new ZmqStream(address);
    this.executor = Executors.newFixedThreadPool(DEFAULT_MAX_THREADS);
  }

  @Override
  public final void run() {
    boolean stopped = false;
    try {
      this.register();
      int outStandingTx = 0;
      while (!stopped) {
        int enqueueCount = 0;
        int dequeueCount = 0;
        try {
          Message inMessage = this.stream.receive(1);
          while (inMessage != null) {
            if (inMessage.getMessageType() == Message.MessageType.PING_REQUEST) {
              LOGGER.info("Recieved Ping Message.");
              PingResponse pingResponse = PingResponse.newBuilder().build();
              this.stream.sendBack(Message.MessageType.PING_RESPONSE, inMessage.getCorrelationId(),
                  pingResponse.toByteString());
            } else {
              Runnable processMessage = new ProcessRunnable(inMessage, this.handler, this.outQueue);
              this.executor.submit(processMessage);
              enqueueCount++;
              inMessage = this.stream.receive(1);
            }
          }
          if (inMessage == null) {
            // Then the validator has disconnected and we need to reregister.
            this.register();
          }
        } catch (TimeoutException e) {
          LOGGER.log(Level.FINER, "Nothing to process on this iteration.");
        }
        Map.Entry<String, TpProcessResponse> outPair = outQueue.poll(1, TimeUnit.MILLISECONDS);
        while (outPair != null) {
          this.stream.sendBack(Message.MessageType.TP_PROCESS_REQUEST, outPair.getKey(),
              outPair.getValue().toByteString());
          dequeueCount++;
          outPair = outQueue.poll(1, TimeUnit.MILLISECONDS);
        }
        outStandingTx += enqueueCount;
        outStandingTx -= dequeueCount;
        if (enqueueCount > 0 || dequeueCount > 0 || outStandingTx > 0) {
          LOGGER.info(String.format("Enqueued %s transactions, Dequeued %s responses, outStanding tx=%s", enqueueCount,
              dequeueCount, outStandingTx));
        }
      }
    } catch (InterruptedException e) {
      LOGGER.info("Processor interrupted, shutting down");
    }

  }

  private void register() {
    LOGGER.info("Registering TP");
    boolean registered = false;
    while (!registered) {
      try {
        TpRegisterRequest registerRequest = TpRegisterRequest.newBuilder()
            .setFamily(this.handler.transactionFamilyName()).addAllNamespaces(this.handler.getNameSpaces())
            .setVersion(this.handler.getVersion()).setMaxOccupancy(DEFAULT_MAX_THREADS).build();
        Future fut = this.stream.send(Message.MessageType.TP_REGISTER_REQUEST, registerRequest.toByteString());
        fut.getResult();
        registered = true;
      } catch (InterruptedException | ValidatorConnectionError e) {
        LOGGER.log(Level.WARNING, "Failed to register with validator, retrying...", e);
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
          LOGGER.log(Level.WARNING, "Invalid Transaction: " + ite.toString(), ite);
          builder.setStatus(TpProcessResponse.Status.INVALID_TRANSACTION);
          builder.setMessage(ite.getMessage());
          if (ite.getExtendedData() != null) {
            builder.setExtendedData(ByteString.copyFrom(ite.getExtendedData()));
          }
        } catch (InternalError ie) {
          LOGGER.log(Level.WARNING, "State Exception!: " + ie.toString(), ie);
          builder.setStatus(TpProcessResponse.Status.INTERNAL_ERROR);
          builder.setMessage(ie.getMessage());
          if (ie.getExtendedData() != null) {
            builder.setExtendedData(ByteString.copyFrom(ie.getExtendedData()));
          }
        }
        responses.put(Map.entry(message.getCorrelationId(), builder.build()));
      } catch (InvalidProtocolBufferException e) {
        LOGGER.log(Level.WARNING, "InvalidProtocolBufferException!: " + e.toString(), e);
      } catch (InterruptedException e) {
        LOGGER.log(Level.WARNING, "Interrupted while queueing a response!: " + e.toString(), e);
      }
    }

  }

}
