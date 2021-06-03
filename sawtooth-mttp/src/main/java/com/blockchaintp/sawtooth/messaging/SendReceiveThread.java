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

package com.blockchaintp.sawtooth.messaging;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.google.protobuf.InvalidProtocolBufferException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZContext;
import org.zeromq.ZFrame;
import org.zeromq.ZLoop;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.PollItem;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMsg;

import sawtooth.sdk.messaging.Future;
import sawtooth.sdk.messaging.FutureError;
import sawtooth.sdk.processor.exceptions.ValidatorConnectionError;
import sawtooth.sdk.protobuf.Message;

/**
 * An internal messaging implementation used by the Stream class.
 */
class SendReceiveThread implements Runnable {

  private static final Logger LOGGER = LoggerFactory.getLogger(SendReceiveThread.class);

  /**
   * The address to connect to.
   */
  private final String url;

  /**
   * The ZMQ dealer socket that will connect to the validator's Router socket.
   */
  private ZMQ.Socket socket;

  /**
   * Lock associated with the Condition.
   */
  private final Lock lock = new ReentrantLock();

  /**
   * Condition to wait for setup to happen.
   */
  private final Condition condition = lock.newCondition();

  /**
   * Futures to be resolved.
   */
  private final ConcurrentHashMap<String, Future> futures;

  /**
   * Incoming messages.
   */
  private final LinkedBlockingQueue<MessageWrapper> receiveQueue;

  /**
   * Outgoing messages.
   */
  private final LinkedBlockingQueue<Message> sendQueue;

  /**
   * The Zeromq context.
   */
  private ZContext context;

  private ExecutorService threadpool;

  /**
   * Constructor.
   *
   * @param address  The address to connect to.
   * @param hashMap  The futures to resolve.
   * @param receiver The incoming messages.
   */
  SendReceiveThread(final String address, final ConcurrentHashMap<String, Future> hashMap,
      final LinkedBlockingQueue<MessageWrapper> receiver) {
    super();
    this.url = address;
    this.futures = hashMap;
    this.receiveQueue = receiver;
    this.sendQueue = new LinkedBlockingQueue<>();
    this.context = null;
    this.threadpool = Executors.newCachedThreadPool();
  }

  /**
   * Inner class for passing messages.
   */
  class MessageWrapper {
    /**
     * The protobuf Message.
     */
    private final Message message;

    /**
     * Constructor.
     *
     * @param msg The protobuf Message.
     */
    MessageWrapper(final Message msg) {
      this.message = msg;
    }

    /**
     * Return the Message associated with this MessageWrapper.
     *
     * @return Message the message.
     */
    public Message getMessage() {
      return message;
    }
  }

  /**
   * DisconnectThread is run to handle the validator disconnecting on the other
   * side of the ZMQ connection.
   */
  private class DisconnectThread implements Runnable {

    /**
     * Queue to put newly received messages on.
     */
    private final LinkedBlockingQueue<MessageWrapper> receiveQueue;

    /**
     * Futures to be resolved.
     */
    private final ConcurrentHashMap<String, Future> futures;

    private final Socket monitorSocket;

    /**
     * Constructor.
     *
     * @param receiver The queue that receives new messages.
     * @param hashMap  The futures that will be resolved.
     */
    DisconnectThread(final LinkedBlockingQueue<MessageWrapper> receiver,
        final ConcurrentHashMap<String, Future> hashMap, final Socket monitor) {
      this.receiveQueue = SendReceiveThread.this.receiveQueue;
      this.futures = SendReceiveThread.this.futures;
      this.monitorSocket = monitor;
    }

    /**
     * Put a key and associated value in the futures.
     *
     * @param key   correlation id
     * @param value the future.
     */
    void putInFutures(final String key, final Future value) {
      this.futures.put(key, value);
    }

    /**
     * Clear the receiveQueue of all messages, in anticipation of sending an error
     * message.
     */
    void clearReceiveQueue() {
      this.receiveQueue.clear();
    }

    /**
     * Put a message in the ReceiveQueue.
     *
     * @param wrapper The message wrapper.
     * @throws InterruptedException An Interrupt happened during the method call.
     */
    void putInReceiveQueue(final MessageWrapper wrapper) throws InterruptedException {
      this.receiveQueue.put(wrapper);
    }

    /**
     * Return an enumeration of the coorelation ids.
     *
     * @return coorelation ids.
     */
    ConcurrentHashMap.KeySetView<String, Future> getFuturesKeySet() {
      return this.futures.keySet();
    }

    @Override
    public void run() {
      while (true) {
        // blocks until disconnect event recieved
        final var event = ZMQ.Event.recv(this.monitorSocket);
        if (event.getEvent() == ZMQ.EVENT_DISCONNECTED) {
          try {
            final var disconnectMsg = new MessageWrapper(null);
            for (final String key : this.getFuturesKeySet()) {
              final Future future = new FutureError();
              this.putInFutures(key, future);
            }
            this.clearReceiveQueue();
            this.putInReceiveQueue(disconnectMsg);
          } catch (final InterruptedException ie) {
            LOGGER.warn("Interrupted while sending/receiving messages", ie);
            Thread.currentThread().interrupt();
            break;
          }
        }
      }
    }

  }

  /**
   * A handler for sending messages safely.
   */
  private class Sender implements ZLoop.IZLoopHandler {

    /**
     * The queue inbound to thins handler where messages to be sent are added.
     */
    private final LinkedBlockingQueue<Message> sendQueue;

    /**
     * Contructor.
     *
     * @param messages
     */
    Sender(final LinkedBlockingQueue<Message> messages) {
      this.sendQueue = messages;
    }

    @Override
    public int handle(final ZLoop loop, final PollItem item, final Object arg) {
      final ArrayList<Message> toSend = new ArrayList<>();
      this.sendQueue.drainTo(toSend);
      for (final Message m : toSend) {
        final var msg = new ZMsg();
        msg.add(m.toByteString().toByteArray());
        msg.send(socket);
      }
      return 0;
    }
  }

  /**
   * Inner class for receiving messages.
   */
  private class Receiver implements ZLoop.IZLoopHandler {

    /**
     * The futures that will be resolved.
     */
    private final ConcurrentHashMap<String, Future> futures;

    /**
     * The threadsafe queue that new messages will be put on.
     */
    private final LinkedBlockingQueue<MessageWrapper> receiveQueue;

    /**
     * Constructor.
     *
     * @param hashMap  The futures that will be resolved.
     * @param receiver The new messages that will get added to.
     */
    Receiver(final ConcurrentHashMap<String, Future> hashMap, final LinkedBlockingQueue<MessageWrapper> receiver) {
      this.futures = hashMap;
      this.receiveQueue = receiver;
    }

    @Override
    public int handle(final ZLoop loop, final ZMQ.PollItem item, final Object arg) {
      final var msg = ZMsg.recvMsg(item.getSocket());
      final Iterator<ZFrame> multiPartMessage = msg.iterator();

      final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      while (multiPartMessage.hasNext()) {
        final ZFrame frame = multiPartMessage.next();
        try {
          byteArrayOutputStream.write(frame.getData());
        } catch (final IOException ioe) {
          LOGGER.warn("IOException handling message", ioe);
        }
      }
      try {
        final var message = Message.parseFrom(byteArrayOutputStream.toByteArray());
        if (this.futures.containsKey(message.getCorrelationId())) {
          final var future = this.futures.get(message.getCorrelationId());
          future.setResult(message.getContent());
          this.futures.remove(message.getCorrelationId(), future);
        } else {
          final var wrapper = new MessageWrapper(message);
          this.receiveQueue.put(wrapper);
        }
      } catch (final InterruptedException ie) {
        LOGGER.warn("Interrupted while handling a message", ie);
        Thread.currentThread().interrupt();
      } catch (final InvalidProtocolBufferException ipe) {
        LOGGER.warn("Failed to parse message when handling", ipe);
      } catch (final ValidatorConnectionError vce) {
        LOGGER.warn("Problem with the connection to the validator", vce);
      }

      return 0;
    }
  }

  @Override
  public void run() {
    this.context = new ZContext();
    socket = this.context.createSocket(ZMQ.DEALER);
    socket.monitor("inproc://monitor.s", ZMQ.EVENT_DISCONNECTED);
    final var monitor = this.context.createSocket(ZMQ.PAIR);
    monitor.connect("inproc://monitor.s");

    this.threadpool.submit(new DisconnectThread(this.receiveQueue, this.futures, monitor));

    socket.setIdentity((this.getClass().getName() + UUID.randomUUID().toString()).getBytes());
    socket.connect(url);
    lock.lock();
    try {
      condition.signalAll();
    } finally {
      lock.unlock();
    }
    final var eventLoop = new ZLoop();
    final ZMQ.PollItem pollItem = new ZMQ.PollItem(socket, ZMQ.Poller.POLLIN);
    eventLoop.addPoller(pollItem, new Receiver(futures, receiveQueue), new Object());
    eventLoop.addTimer(1, 0, new Sender(this.sendQueue), new Object());
    eventLoop.start();
    this.threadpool.shutdown();
  }

  /**
   * Used by the Stream class to send a message.
   *
   * @param message protobuf Message
   */
  public final void sendMessage(final Message message) {
    if (message != null) {
      try {
        this.sendQueue.put(message);
      } catch (InterruptedException e) {
        LOGGER.warn("Interrupted while sending message", e);
        Thread.currentThread().interrupt();
      }
    }
  }

  /**
   * Ends the zmq communication.
   */
  public void stop() {
    this.socket.close();
    this.context.destroy();
  }

}
