package com.blockchaintp.sawtooth.daml.rpc.events;

import java.util.UUID;

import org.zeromq.ZContext;
import org.zeromq.ZLoop;
import org.zeromq.ZMQ;
import org.zeromq.ZMsg;
import org.zeromq.ZMQ.Socket;

import com.google.protobuf.Message;

/**
 * An implementation of the ZMQDelegate.
 *
 */
public class ZMQDelegateImpl implements ZMQDelegate {

  private String zmqUrl;
  private final ZContext context;
  private final Socket socket;
  private final ZLoop eventLoop;
  private final ZMQ.Socket monitor;

  /**
   * An implementation of the ZMQDelegate for the provided url.
   * @param url the zmq url
   */
  public ZMQDelegateImpl(final String url) {
    this.zmqUrl = url;
    this.context = new ZContext();
    socket = this.context.createSocket(ZMQ.DEALER);
    socket.setIdentity((this.getClass().getName() + UUID.randomUUID().toString()).getBytes());
    socket.connect(this.zmqUrl);
    eventLoop = new ZLoop();
    this.socket.monitor("inproc://monitor.s", ZMQ.EVENT_DISCONNECTED);
    monitor = this.context.createSocket(ZMQ.PAIR);
    monitor.connect("inproc://monitor.s");
  }

  @Override
  public final void addPoller(final DamlLogEventHandler damlLogEventHandler) {
    ZMQ.PollItem pollItem = new ZMQ.PollItem(this.socket, ZMQ.Poller.POLLIN);
    eventLoop.addPoller(pollItem, damlLogEventHandler, new Object());
  }

  @Override
  public final void start() {
    this.eventLoop.start();
  }

  @Override
  public final void sendMessage(final Message m) {
    ZMsg msg = new ZMsg();
    msg.add(m.toByteString().toByteArray());
    msg.send(this.socket);
  }

  @Override
  public final Socket getMonitor() {
    return this.monitor;
  }
}
