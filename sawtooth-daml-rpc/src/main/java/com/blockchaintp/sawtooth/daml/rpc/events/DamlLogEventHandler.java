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
package com.blockchaintp.sawtooth.daml.rpc.events;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import org.reactivestreams.Publisher;
import org.zeromq.ZFrame;
import org.zeromq.ZLoop;
import org.zeromq.ZMQ.PollItem;
import org.zeromq.ZMsg;

import com.blockchaintp.sawtooth.daml.rpc.SawtoothTransactionsTracer;
import com.blockchaintp.sawtooth.daml.util.EventConstants;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntry;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntryId;
import com.daml.ledger.participant.state.kvutils.KeyValueCommitting;
import com.daml.ledger.participant.state.v1.Offset;
import com.daml.ledger.participant.state.v1.Update;
import com.daml.ledger.participant.state.v1.Update.Heartbeat;
import com.digitalasset.daml.lf.data.Time.Timestamp;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;

import io.reactivex.processors.UnicastProcessor;
import sawtooth.sdk.messaging.Future;
import sawtooth.sdk.messaging.Stream;
import sawtooth.sdk.messaging.ZmqStream;
import sawtooth.sdk.processor.exceptions.ValidatorConnectionError;
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

  private static final int DEFAULT_TIMEOUT = 10;
  private static final Logger LOGGER = Logger.getLogger(DamlLogEventHandler.class.getName());
  private static final String[] SUBSCRIBE_SUBJECTS = new String[] {EventConstants.SAWTOOTH_BLOCK_COMMIT_SUBJECT,
      EventConstants.DAML_LOG_EVENT_SUBJECT,
      com.blockchaintp.sawtooth.timekeeper.util.EventConstants.TIMEKEEPER_EVENT_SUBJECT};

  private Collection<EventSubscription> subscriptions;
  private Collection<String> lastBlockIds;

  private final UnicastProcessor<Tuple2<Offset, Update>> processor;

  private Stream stream;
  private LogEntryTransformer transformer;
  private SawtoothTransactionsTracer tracer = null;

  /**
   * Build a handler for the given zmqUrl.
   * @param zmqUrl the zmq address to connect to
   */
  public DamlLogEventHandler(final String zmqUrl) {
    this(new ZmqStream(zmqUrl));
    LOGGER.info(String.format("Connecting to validator at %s", zmqUrl));
  }

  /**
   * Build a handler based on the given Stream.
   * @param argStream the delegate to use
   */
  public DamlLogEventHandler(final Stream argStream) {
    this(argStream, new ArrayList<String>());
  }

  /**
   * Build a handler based on the given delegate.and last known block ids.
   * @param argStream the delegate to use
   * @param blockIds  the last known block ids for this event handler
   */
  public DamlLogEventHandler(final Stream argStream, final Collection<String> blockIds) {
    this(argStream, blockIds, new ParticipantStateLogEntryTransformer());
  }

  /**
   * Build a handler based on the given delegate.and last known block ids.
   * @param argStream the delegate to use
   * @param blockIds  the last known block ids for this event handler
   * @param transform the transformer to use
   */
  public DamlLogEventHandler(final Stream argStream, final Collection<String> blockIds,
      final LogEntryTransformer transform) {
    this.transformer = transform;
    this.subscriptions = new ArrayList<EventSubscription>();
    for (String subject : SUBSCRIBE_SUBJECTS) {
      EventSubscription evtSubscription = EventSubscription.newBuilder().setEventType(subject).build();
      this.subscriptions.add(evtSubscription);
    }
    this.stream = argStream;
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
    if (this.tracer != null) {
      String jsonOut = JsonFormat.printer().print(evtList);
      this.tracer.putReadTransactions(jsonOut);
    }
    Collection<Tuple2<Offset, Update>> tuplesToReturn = new ArrayList<>();

    long blockNum = 0;
    for (Event evt : evtList.getEventsList()) {
      Map<String, String> attrMap = eventAttributeMap(evt);
      if (evt.getEventType().equals(EventConstants.SAWTOOTH_BLOCK_COMMIT_SUBJECT)) {
        blockNum = Long.parseLong(attrMap.get(EventConstants.SAWTOOTH_BLOCK_NUM_EVENT_ATTRIBUTE));
        LOGGER.fine(String.format("Received block-commit block_num=%s", blockNum));
      }
    }
    long updateCounter = 1;
    for (Event evt : evtList.getEventsList()) {
      Map<String, String> attrMap = eventAttributeMap(evt);
      if (evt.getEventType().equals(EventConstants.DAML_LOG_EVENT_SUBJECT)) {
        String entryIdStr = attrMap.get(EventConstants.DAML_LOG_ENTRY_ID_EVENT_ATTRIBUTE);
        long offsetCounter = Long.parseLong(attrMap.get(EventConstants.DAML_OFFSET_EVENT_ATTRIBUTE));
        ByteString entryIdVal = ByteString.copyFromUtf8(entryIdStr);
        DamlLogEntryId id = DamlLogEntryId.newBuilder().setEntryId(entryIdVal).build();
        DamlLogEntry logEntry = KeyValueCommitting.unpackDamlLogEntry(evt.getData());
        Update logEntryToUpdate = this.transformer.logEntryUpdate(id, logEntry);
        Offset offset = new Offset(new long[] {blockNum, offsetCounter, updateCounter});
        updateCounter++;
        Tuple2<Offset, Update> updateTuple = Tuple2.apply(offset, logEntryToUpdate);
        tuplesToReturn.add(updateTuple);
        LOGGER.info(String.format("Sending update at offset=%s", offset));
      }
    }
    // Only send heartbeats if we haven't sent anything
    if (tuplesToReturn.isEmpty()) {
      for (Event evt : evtList.getEventsList()) {
        Map<String, String> attrMap = eventAttributeMap(evt);
        if (evt.getEventType()
            .equals(com.blockchaintp.sawtooth.timekeeper.util.EventConstants.TIMEKEEPER_EVENT_SUBJECT)) {
          String microsStr = attrMap
              .get(com.blockchaintp.sawtooth.timekeeper.util.EventConstants.TIMEKEEPER_MICROS_ATTRIBUTE);
          long microseconds = Long.valueOf(microsStr);
          Heartbeat heartbeat = new Heartbeat(new Timestamp(microseconds));
          Offset hbOffset = new Offset(new long[] {blockNum, 0});
          Tuple2<Offset, Update> updateTuple = Tuple2.apply(hbOffset, heartbeat);
          // Only send the most recent heartbeat
          if (!tuplesToReturn.isEmpty()) {
            tuplesToReturn.clear();
          }
          tuplesToReturn.add(updateTuple);
          LOGGER.info(String.format("Sending heartbeat at offset=%s", hbOffset));

        }
      }
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
    LOGGER.fine("Handling message...");

    ZMsg msg = ZMsg.recvMsg(item.getSocket());
    Iterator<ZFrame> multiPartMessage = msg.iterator();

    while (multiPartMessage.hasNext()) {
      ZFrame frame = multiPartMessage.next();
      try {
        Message message = Message.parseFrom(frame.getData());
        processMessage(message);
      } catch (InvalidProtocolBufferException exc) {
        LOGGER.warning(exc.getMessage());
      }
    }
    return 0;
  }

  protected final void processMessage(final Message message) throws InvalidProtocolBufferException {
    LOGGER.fine("Process message...");

    if (message.getMessageType().equals(MessageType.CLIENT_EVENTS)) {
      EventList evtList = EventList.parseFrom(message.getContent());
      Collection<Tuple2<Offset, Update>> updates = eventToUpdates(evtList);
      for (Tuple2<Offset, Update> u : updates) {
        this.processor.onNext(u);
      }
    } else {
      LOGGER.warning(String.format("Unexpected message type: %s", message.getMessageType()));
    }
  }

  @Override
  public final void run() {
    sendSubscribe();
    while (true) {
      Message receivedMsg = null;
      try {
        receivedMsg = this.stream.receive(DEFAULT_TIMEOUT);
      } catch (TimeoutException exc) {
        LOGGER.fine("Timeout waiting for message");
        receivedMsg = null;
      }
      if (receivedMsg != null) {
        try {
          processMessage(receivedMsg);
        } catch (InvalidProtocolBufferException exc) {
          LOGGER.warning(String.format("Error unmarshalling message of type: %s", receivedMsg.getMessageType()));
        }
      }
    }
  }

  /**
   * Send a subscribe message to the validator.
   */
  public final void sendSubscribe() {
    ClientEventsSubscribeRequest cesReq = ClientEventsSubscribeRequest.newBuilder().addAllSubscriptions(subscriptions)
        .addAllLastKnownBlockIds(lastBlockIds).build();
    Future resp = this.stream.send(MessageType.CLIENT_EVENTS_SUBSCRIBE_REQUEST, cesReq.toByteString());
    LOGGER.info("Waiting for subscription response...");
    try {
      ByteString result = null;
      while (result == null) {
        try {
          result = resp.getResult(DEFAULT_TIMEOUT);
        } catch (TimeoutException exc) {
          LOGGER.warning("Still waiting for subscription response...");
        }
      }
      LOGGER.info("Subscription response received...");

    } catch (InterruptedException | ValidatorConnectionError exc) {
      LOGGER.warning(exc.getMessage());
    }
  }

  /**
   * Send an unsubscribe message to the validator.
   */
  public final void sendUnsubscribe() {
    ClientEventsUnsubscribeRequest ceuReq = ClientEventsUnsubscribeRequest.newBuilder().build();
    Future resp = this.stream.send(MessageType.CLIENT_EVENTS_UNSUBSCRIBE_REQUEST, ceuReq.toByteString());
    try {
      resp.getResult();
      LOGGER.info("Unsubscribed...");
    } catch (InterruptedException | ValidatorConnectionError exc) {
      LOGGER.warning(exc.getMessage());
    }
  }

  /**
   * Set a tracer for this event handler.
   * @param trace the tracer
   */
  public final void setTracer(final SawtoothTransactionsTracer trace) {
    this.tracer = trace;
  }

}
