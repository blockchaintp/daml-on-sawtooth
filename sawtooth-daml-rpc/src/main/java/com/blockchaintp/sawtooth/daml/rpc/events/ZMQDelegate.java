package com.blockchaintp.sawtooth.daml.rpc.events;

import org.zeromq.ZMQ.Event;
import org.zeromq.ZMQ.Socket;

import sawtooth.sdk.protobuf.Message;

/**
 * An interface to indirect interaction with the ZMQ implementation. Useful for
 * dependency injection and unit testing.
 */
public interface ZMQDelegate {

  /**
   * Set this DamlLogEventHandler to be used in the polling for this delegate.
   * @param damlLogEventHandler the log event handler
   */
  void addPoller(DamlLogEventHandler damlLogEventHandler);

  /**
   * Start the ZMQ polling.
   */
  void start();

  /**
   * Send a message along the ZMQ channel.
   * @param m the message to send
   */
  void sendMessage(Message m);

  /**
   * Get the ZMQ monitor socket.
   * @return the monitor socket
   */
  Socket getMonitor();

  /**
   * Receieve a ZMQ event blocking until done
   * @return
   */
  Event monitorRecv();

}
