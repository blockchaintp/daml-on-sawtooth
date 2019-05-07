package com.blockchaintp.sawtooth.daml.rpc.events;

import org.reactivestreams.Processor;
import org.zeromq.ZMQ;

/**
 * A Thread which monitors a ZMQ channel, and when a disconnect is received it
 * will complete the processor.
 * @param <T> the type of processor to be completed.
 */
public class MonitorDisconnectThread<T> extends Thread {

  private Processor<T, T> processor;
  private ZMQDelegate zmqDelegate;

  /**
   * A Thread which monitors a ZMQ channel, and when a disconnect is received it
   * will complete the processor.
   * @param delegate the zmq delegate to use
   * @param processorToCleanup the processor to cleanup
   */
  public MonitorDisconnectThread(final ZMQDelegate delegate, final Processor<T, T> processorToCleanup) {
    this.zmqDelegate = delegate;
    this.processor = processorToCleanup;

  }

  @Override
  public final void run() {
    while (true) {
      // blocks until disconnect event recieved
      ZMQ.Event event = ZMQ.Event.recv(this.zmqDelegate.getMonitor());
      if (event.getEvent() == ZMQ.EVENT_DISCONNECTED) {
        this.processor.onComplete();
      }
    }
  }

}
