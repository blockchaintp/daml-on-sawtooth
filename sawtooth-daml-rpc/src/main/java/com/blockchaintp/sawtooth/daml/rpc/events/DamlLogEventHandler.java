package com.blockchaintp.sawtooth.daml.rpc.events;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.reactivestreams.Publisher;
import org.zeromq.ZFrame;
import org.zeromq.ZLoop;
import org.zeromq.ZMQ.PollItem;
import org.zeromq.ZMsg;

import com.blockchaintp.sawtooth.daml.util.EventConstants;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntry;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntryId;
import com.daml.ledger.participant.state.v1.Offset;
import com.daml.ledger.participant.state.v1.Update;
import com.daml.ledger.participant.state.v1.Update.Heartbeat;
import com.digitalasset.daml.lf.data.Time.Timestamp;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import io.reactivex.processors.UnicastProcessor;
import sawtooth.sdk.protobuf.ClientEventsSubscribeRequest;
import sawtooth.sdk.protobuf.ClientEventsUnsubscribeRequest;
import sawtooth.sdk.protobuf.Event;
import sawtooth.sdk.protobuf.Event.Attribute;
import sawtooth.sdk.protobuf.EventList;
import sawtooth.sdk.protobuf.EventSubscription;
import sawtooth.sdk.protobuf.Message;
import sawtooth.sdk.protobuf.Message.MessageType;
import scala.Tuple2;

/**
 * A thread which subscribes to sawtooth events, receives those events and
 * provides them to a Processor.
 */
public class DamlLogEventHandler implements Runnable, ZLoop.IZLoopHandler {

  private static final Logger LOGGER = Logger.getLogger(DamlLogEventHandler.class.getName());
  private static final String[] SUBSCRIBE_SUBJECTS = new String[] {EventConstants.SAWTOOTH_BLOCK_COMMIT_SUBJECT,
      EventConstants.DAML_LOG_EVENT_SUBJECT,
      com.blockchaintp.sawtooth.timekeeper.util.EventConstants.TIMEKEEPER_EVENT_SUBJECT};

  private Collection<EventSubscription> subscriptions;
  private Collection<String> lastBlockIds;

  private final UnicastProcessor<Tuple2<Offset, Update>> processor;

  private ZMQDelegate zmqDelegate;
  private LogEntryTransformer transformer;

  /**
   * Build a handler for the given zmqUrl.
   * @param zmqUrl the zmq address to connect to
   */
  public DamlLogEventHandler(final String zmqUrl) {
    this(new ZMQDelegateImpl(zmqUrl));
  }

  /**
   * Build a handler based on the given delegate.
   * @param delegate the delegate to use
   */
  public DamlLogEventHandler(final ZMQDelegate delegate) {
    this(delegate, new ArrayList<String>());
  }

  /**
   * Build a handler based on the given delegate.and last known block ids.
   * @param delegate the delegate to use
   * @param blockIds the last known block ids for this event handler
   */
  public DamlLogEventHandler(final ZMQDelegate delegate, final Collection<String> blockIds) {
    this(delegate, blockIds, new ParticipantStateLogEntryTransformer());
  }

  /**
   * Build a handler based on the given delegate.and last known block ids.
   * @param delegate  the delegate to use
   * @param blockIds  the last known block ids for this event handler
   * @param transform the transformer to use
   */
  public DamlLogEventHandler(final ZMQDelegate delegate, final Collection<String> blockIds,
      final LogEntryTransformer transform) {
    this.transformer = transform;
    this.subscriptions = new ArrayList<EventSubscription>();
    for (String subject : SUBSCRIBE_SUBJECTS) {
      EventSubscription evtSubscription = EventSubscription.newBuilder().setEventType(subject).build();
      this.subscriptions.add(evtSubscription);
    }
    this.zmqDelegate = delegate;
    // TODO add a cancel call back
    this.processor = UnicastProcessor.create();
    this.lastBlockIds = blockIds;
  }

  private Map<String, String> eventAttributeMap(final Event evt) {
    Map<String, String> attrMap = new HashMap<>();
    List<Attribute> attributes = evt.getAttributesList();
    for (Attribute attr : attributes) {
      attrMap.put(attr.getKey(), attr.getValue());
    }
    return attrMap;
  }

  private Collection<Tuple2<Offset, Update>> eventToUpdates(final EventList evtList)
      throws InvalidProtocolBufferException {
    long blockNum = 0;
    Collection<Update> updates = new ArrayList<Update>();
    for (Event evt : evtList.getEventsList()) {
      Map<String, String> attrMap = eventAttributeMap(evt);
      if (evt.getEventType().equals(EventConstants.SAWTOOTH_BLOCK_COMMIT_SUBJECT)) {
        if (attrMap.containsKey(EventConstants.SAWTOOTH_BLOCK_NUM_EVENT_ATTRIBUTE)) {
          blockNum = Long.parseLong(attrMap.get(EventConstants.SAWTOOTH_BLOCK_NUM_EVENT_ATTRIBUTE));
        }
      } else if (evt.getEventType().equals(EventConstants.DAML_LOG_EVENT_SUBJECT)) {
        // parse the log entries into updates
        if (attrMap.containsKey(EventConstants.DAML_LOG_ENTRY_ID_EVENT_ATTRIBUTE)) {
          String entryIdStr = attrMap.get(EventConstants.DAML_LOG_ENTRY_ID_EVENT_ATTRIBUTE);
          ByteString entryIdVal = ByteString.copyFromUtf8(entryIdStr);
          DamlLogEntryId id = DamlLogEntryId.newBuilder().setEntryId(entryIdVal).build();
          DamlLogEntry logEntry = DamlLogEntry.parseFrom(evt.getData());
          Update logEntryToUpdate = this.transformer.logEntryUpdate(id, logEntry);
          updates.add(logEntryToUpdate);
        } else if (evt.getEventType()
            .equals(com.blockchaintp.sawtooth.timekeeper.util.EventConstants.TIMEKEEPER_EVENT_SUBJECT)) {
          String microsStr = attrMap
              .get(com.blockchaintp.sawtooth.timekeeper.util.EventConstants.TIMEKEEPER_MICROS_ATTRIBUTE);
          long microseconds = Long.valueOf(microsStr);
          Heartbeat heartbeat = new Heartbeat(new Timestamp(microseconds));
          updates.add(heartbeat);
        }
      }
    }

    Collection<Tuple2<Offset, Update>> tuplesToReturn = new ArrayList<>();
    if (blockNum != 0) {
      for (Update u : updates) {
        Offset offset = new Offset(new long[] {blockNum, tuplesToReturn.size()});
        Tuple2<Offset, Update> updateTuple = Tuple2.apply(offset, u);
        tuplesToReturn.add(updateTuple);
      }
    } else {
      LOGGER.log(Level.WARNING, "No block num received in the last event update");
    }
    return tuplesToReturn;
  }

  /**
   * Fetch the publisher for the event subscription this handler controls.
   * @return the publisher
   */
  public final Publisher<Tuple2<Offset, Update>> getPublisher() {
    return processor;
  }

  @Override
  public final int handle(final ZLoop loop, final PollItem item, final Object arg) {
    ZMsg msg = ZMsg.recvMsg(item.getSocket());
    Iterator<ZFrame> multiPartMessage = msg.iterator();

    while (multiPartMessage.hasNext()) {
      ZFrame frame = multiPartMessage.next();
      try {
        Message message = Message.parseFrom(frame.getData());
        processMessage(message);
      } catch (InvalidProtocolBufferException exc) {
        LOGGER.log(Level.WARNING, exc.getMessage());
      }
    }
    return 0;
  }

  protected final void processMessage(final Message message) throws InvalidProtocolBufferException {
    if (message.getMessageType().equals(MessageType.CLIENT_EVENTS)) {
      EventList evtList = EventList.parseFrom(message.getContent());
      Collection<Tuple2<Offset, Update>> updates = eventToUpdates(evtList);
      for (Tuple2<Offset, Update> u : updates) {
        this.processor.onNext(u);
      }
    } else {
      LOGGER.log(Level.WARNING, "Unexpected message type: {}", message.getMessageType());
    }
  }

  @Override
  public final void run() {
    MonitorDisconnectThread<Tuple2<Offset, Update>> disconnectThread = new MonitorDisconnectThread<>(this.zmqDelegate,
        this.processor);
    disconnectThread.start();
    this.zmqDelegate.addPoller(this);
    sendSubscribe();
    this.zmqDelegate.start();
    sendUnsubscribe();
    this.processor.onComplete();
  }

  /**
   * Send a subscribe message to the validator.
   */
  public final void sendSubscribe() {
    ClientEventsSubscribeRequest cesReq = ClientEventsSubscribeRequest.newBuilder().addAllSubscriptions(subscriptions)
        .addAllLastKnownBlockIds(lastBlockIds).build();
    Message message = Message.newBuilder().setMessageType(MessageType.CLIENT_EVENTS_SUBSCRIBE_REQUEST)
        .setContent(cesReq.toByteString()).build();
    this.zmqDelegate.sendMessage(message);
  }

  /**
   * Send an unsubscribe message to the validator.
   */
  public final void sendUnsubscribe() {
    ClientEventsUnsubscribeRequest ceuReq = ClientEventsUnsubscribeRequest.newBuilder().build();
    Message message = Message.newBuilder().setMessageType(MessageType.CLIENT_EVENTS_UNSUBSCRIBE_REQUEST)
        .setContent(ceuReq.toByteString()).build();
    this.zmqDelegate.sendMessage(message);
  }

}
