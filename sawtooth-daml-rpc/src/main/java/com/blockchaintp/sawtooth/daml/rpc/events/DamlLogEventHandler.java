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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

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
import sawtooth.sdk.protobuf.ClientBlockGetByNumRequest;
import sawtooth.sdk.protobuf.ClientBlockGetResponse;
import sawtooth.sdk.protobuf.ClientEventsSubscribeRequest;
import sawtooth.sdk.protobuf.ClientEventsSubscribeResponse;
import sawtooth.sdk.protobuf.ClientEventsUnsubscribeRequest;
import sawtooth.sdk.protobuf.ClientStateGetRequest;
import sawtooth.sdk.protobuf.ClientStateGetResponse;
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
  private static final String[] SUBSCRIBE_SUBJECTS = new String[] { EventConstants.SAWTOOTH_BLOCK_COMMIT_SUBJECT,
      EventConstants.DAML_LOG_EVENT_SUBJECT,
      com.blockchaintp.sawtooth.timekeeper.util.EventConstants.TIMEKEEPER_EVENT_SUBJECT };
  private static final int COMPRESS_BUFFER_SIZE = 1024;

  private Collection<EventSubscription> subscriptions;

  private final List<UnicastProcessor<Tuple2<Offset, Update>>> processors;
  private boolean subscribed = false;
  private Stream stream;
  private LogEntryTransformer transformer;
  private SawtoothTransactionsTracer tracer = null;
  private Offset lastOffset;

  /**
   * Build a handler for the given zmqUrl.
   *
   * @param zmqUrl the zmq address to connect to
   */
  public DamlLogEventHandler(final String zmqUrl) {
    this(new ZmqStream(zmqUrl));
    LOGGER.info(String.format("Connecting to validator at %s", zmqUrl));
  }

  /**
   * Build a handler based on the given Stream.
   *
   * @param argStream the delegate to use
   */
  public DamlLogEventHandler(final Stream argStream) {
    this(argStream, new ParticipantStateLogEntryTransformer());
  }

  /**
   * Build a handler based on the given delegate.and last known block ids.
   *
   * @param argStream the delegate to use
   * @param transform the transformer to use
   */
  public DamlLogEventHandler(final Stream argStream, final LogEntryTransformer transform) {
    this.transformer = transform;
    this.subscriptions = new ArrayList<EventSubscription>();
    for (String subject : SUBSCRIBE_SUBJECTS) {
      EventSubscription evtSubscription = EventSubscription.newBuilder().setEventType(subject).build();
      this.subscriptions.add(evtSubscription);
    }
    this.stream = argStream;
    // TODO add a cancel call back
    this.processors = Collections.synchronizedList(new ArrayList<>());
    this.lastOffset = Offset.apply(new long[] { 0 });
  }

  private String getBlockIdByOffset(Offset offset) {
    String[] offsetSplit = offset.toString().split("-");
    // Block 1 is the genesis block, While unlikely the first possible interesting
    // data is at block 2
    Long blockNum = Math.max(2, Long.valueOf(offsetSplit[0]));

    ClientBlockGetByNumRequest bgbn = ClientBlockGetByNumRequest.newBuilder().setBlockNum(blockNum).build();
    Future resp = this.stream.send(MessageType.CLIENT_BLOCK_GET_BY_NUM_REQUEST, bgbn.toByteString());

    String retBlockId = null;
    LOGGER.fine(String.format("Waiting for ClientBlockGetResponse for block_num: %s", blockNum));
    try {
      ByteString result = null;
      while (result == null) {
        try {
          result = resp.getResult(DEFAULT_TIMEOUT);
          ClientBlockGetResponse response = ClientBlockGetResponse.parseFrom(result);
          switch (response.getStatus()) {
          case OK:
            LOGGER.fine("ClientBlockGetResponse received...");
            retBlockId = response.getBlock().getHeaderSignature();
            break;
          case NO_RESOURCE:
            LOGGER.info(String.format("NO_RESOURCE received from ClientBlockGetResponse: %s", response.toString()));
            return null;
          case INTERNAL_ERROR:
          case UNRECOGNIZED:
          case INVALID_ID:
          case STATUS_UNSET:
          default:
            LOGGER.severe(
                String.format("Invalid response received from ClientBlockGetByNumRequest: %s", response.getStatus()));
          }
        } catch (TimeoutException exc) {
          LOGGER.warning("Still waiting for ClientBlockGetResponse...");
        }
      }
    } catch (InterruptedException | InvalidProtocolBufferException | ValidatorConnectionError exc) {
      LOGGER.warning(exc.getMessage());
    }
    return retBlockId;
  }

  private Map<String, String> eventAttributeMap(final Event evt) {
    Map<String, String> attrMap = new HashMap<>();
    List<Attribute> attributes = evt.getAttributesList();
    for (Attribute attr : attributes) {
      attrMap.put(attr.getKey(), attr.getValue());
    }
    return attrMap;
  }

  private Collection<Tuple2<Offset, Update>> eventToUpdates(final EventList evtList) throws IOException {
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
    Offset lastOffsetThisBlock = null;
    for (Event evt : evtList.getEventsList()) {
      Map<String, String> attrMap = eventAttributeMap(evt);
      if (evt.getEventType().equals(EventConstants.DAML_LOG_EVENT_SUBJECT)) {
        String entryIdStr = attrMap.get(EventConstants.DAML_LOG_ENTRY_ID_EVENT_ATTRIBUTE);
        long offsetCounter = Long.parseLong(attrMap.get(EventConstants.DAML_OFFSET_EVENT_ATTRIBUTE));
        ByteString entryIdVal = ByteString.copyFromUtf8(entryIdStr);
        DamlLogEntryId id = DamlLogEntryId.newBuilder().setEntryId(entryIdVal).build();
        ByteString evtData = uncompressByteString(evt.getData());
        DamlLogEntry logEntry = KeyValueCommitting.unpackDamlLogEntry(evtData);
        for (Update logEntryToUpdate : this.transformer.logEntryUpdate(id, logEntry)) {
          Offset offset = new Offset(new long[] { blockNum, offsetCounter, updateCounter });
          updateCounter++;
          if (offset.compareTo(this.lastOffset) > 0) {
            Tuple2<Offset, Update> updateTuple = Tuple2.apply(offset, logEntryToUpdate);
            tuplesToReturn.add(updateTuple);
            lastOffsetThisBlock = offset;
            LOGGER.fine(String.format("Sending update at offset=%s", offset));
          } else {
            LOGGER.info(
                String.format("Skip sending offset=%s which is less than lastOffset=%s", offset, this.lastOffset));
          }
        }
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
          Offset hbOffset = new Offset(new long[] { blockNum, 0 });
          if (hbOffset.compareTo(this.lastOffset) > 0) {
            Tuple2<Offset, Update> updateTuple = Tuple2.apply(hbOffset, heartbeat);
            // Only send the most recent heartbeat
            if (!tuplesToReturn.isEmpty()) {
              tuplesToReturn.clear();
            }
            tuplesToReturn.add(updateTuple);
            lastOffsetThisBlock = hbOffset;
            LOGGER.info(String.format("Sending heartbeat at offset=%s", hbOffset));
          } else {
            LOGGER.info(
                String.format("Skip sending offset=%s which is less than lastOffset=%s", hbOffset, this.lastOffset));
          }
        }
      }
    }
    if (null != lastOffsetThisBlock) {
      this.lastOffset = lastOffsetThisBlock;
    }
    return tuplesToReturn;
  }

  /**
   * Fetch the publisher for the event subscription this handler controls.
   *
   * @return the publisher
   */
  public final Publisher<Tuple2<Offset, Update>> getPublisher() {
    UnicastProcessor<Tuple2<Offset, Update>> p = UnicastProcessor.create();
    this.processors.add(p);
    return p;
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
      } catch (IOException exc) {
        LOGGER.warning(exc.getMessage());
      }
    }
    return 0;
  }

  protected final void processMessage(final Message message) throws IOException {
    if (message.getMessageType().equals(MessageType.CLIENT_EVENTS)) {
      EventList evtList = EventList.parseFrom(message.getContent());
      Collection<Tuple2<Offset, Update>> updates = eventToUpdates(evtList);
      for (Tuple2<Offset, Update> u : updates) {
        for (UnicastProcessor<Tuple2<Offset, Update>> proc : this.processors) {
          proc.onNext(u);
        }
      }
    } else {
      LOGGER.warning(String.format("Unexpected message type: %s", message.getMessageType()));
    }
  }

  @Override
  public final void run() {
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
        } catch (IOException exc) {
          LOGGER.warning(String.format("Error unmarshalling message of type: %s", receivedMsg.getMessageType()));
        }
      }
    }
  }

  /**
   * Send a subscribe message to the validator.
   */
  public final void sendSubscribe() {
    this.sendSubscribe(null);
  }

  /**
   * Send a subscribe message to the validator.
   *
   * @param beginAfter the offset to begin subscribing after
   */
  public final void sendSubscribe(final Offset beginAfter) {
    if (this.subscribed) {
      LOGGER.warning("Attempted to subscribe twice");
      return;
    }

    List<String> lastBlockIds = new ArrayList<>();
    if (beginAfter != null) {
      String blockId = getBlockIdByOffset(beginAfter);
      if (blockId != null) {
        lastBlockIds.add(blockId);
      }
      this.lastOffset = beginAfter;
    }

    ClientEventsSubscribeRequest cesReq = ClientEventsSubscribeRequest.newBuilder().addAllSubscriptions(subscriptions)
        .addAllLastKnownBlockIds(lastBlockIds).build();

    Future resp = this.stream.send(MessageType.CLIENT_EVENTS_SUBSCRIBE_REQUEST, cesReq.toByteString());
    LOGGER.fine("Waiting for subscription response...");
    try {
      ByteString result = null;
      while (result == null) {
        try {
          result = resp.getResult(DEFAULT_TIMEOUT);
          ClientEventsSubscribeResponse subscribeResponse = ClientEventsSubscribeResponse.parseFrom(result);
          switch (subscribeResponse.getStatus()) {
          case UNKNOWN_BLOCK:
            throw new RuntimeException(String.format("Unknown blockids in subscription: %s", lastBlockIds));
          case INVALID_FILTER:
            LOGGER.warning("InvalidFilters response received");
          case STATUS_UNSET:
          case OK:
            this.subscribed = true;
            LOGGER.info("Subscription response received...");
            return;
          default:
          }
        } catch (TimeoutException exc) {
          LOGGER.warning("Still waiting for subscription response...");
        }
      }

    } catch (InterruptedException | InvalidProtocolBufferException | ValidatorConnectionError exc) {
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
      LOGGER.fine("Unsubscribed...");
    } catch (InterruptedException | ValidatorConnectionError exc) {
      LOGGER.warning(exc.getMessage());
    }
  }

  /**
   * Set a tracer for this event handler.
   *
   * @param trace the tracer
   */
  public final void setTracer(final SawtoothTransactionsTracer trace) {
    this.tracer = trace;
  }

  private ByteString uncompressByteString(final ByteString compressedInput) throws IOException {
    long uncompressStart = System.currentTimeMillis();
    Inflater inflater = new Inflater();
    byte[] inputBytes = compressedInput.toByteArray();
    inflater.setInput(inputBytes);

    try (ByteArrayOutputStream baos = new ByteArrayOutputStream(inputBytes.length)) {
      byte[] buffer = new byte[COMPRESS_BUFFER_SIZE];
      try {
        while (!inflater.finished()) {
          int bCount = inflater.inflate(buffer);
          baos.write(buffer, 0, bCount);
        }
        inflater.end();

        ByteString bs = ByteString.copyFrom(baos.toByteArray());
        long uncompressStop = System.currentTimeMillis();
        long uncompressTime = uncompressStop - uncompressStart;
        LOGGER.fine(String.format("Uncompressed ByteString time=%s, original_size=%s, new_size=%s", uncompressTime,
            inputBytes.length, baos.size()));
        return bs;
      } catch (DataFormatException exc) {
        LOGGER.severe(String.format("Error uncompressing stream, throwing InternalError! %s", exc.getMessage()));
        throw new IOException(exc.getMessage());
      }
    } catch (IOException exc) {
      LOGGER.severe("ByteArrayOutputStream.close() has thrown an error which should never happen!");
      throw exc;
    }
  }

  /**
   * Get the state at the given address.
   *
   * @param address the address of the state entry
   * @return the data at the address
   */
  public ByteString getState(final String address) {
    ClientStateGetRequest req = ClientStateGetRequest.newBuilder().setAddress(address).build();
    Future resp = stream.send(MessageType.CLIENT_STATE_GET_REQUEST, req.toByteString());

    LOGGER.fine(String.format("Waiting for ClientStateGetResponse for address %s", address));
    try {
      ByteString result = null;
      while (result == null) {
        try {
          result = resp.getResult(DEFAULT_TIMEOUT);
          ClientStateGetResponse response = ClientStateGetResponse.parseFrom(result);
          switch (response.getStatus()) {
          case OK:
            ByteString bs = response.getValue();
            LOGGER.fine(String.format("ClientStateGetResponse received OK for %s=%s", address, bs));
            return bs;
          case NO_RESOURCE:
            LOGGER.info(String.format("Address %s not currently set", address));
            return null;
          case INVALID_ADDRESS:
          case NOT_READY:
          case NO_ROOT:
          case INTERNAL_ERROR:
          case UNRECOGNIZED:
          case STATUS_UNSET:
          case INVALID_ROOT:
          default:
            LOGGER.severe(String.format("Invalid response received from ClientStateGetRequest address=%s response=%s",
                address, response.getStatus()));
            return null;
          }
        } catch (TimeoutException exc) {
          LOGGER.warning(String.format("Still waiting for ClientStateGetResponse address=%s", address));
        }
      }
    } catch (InterruptedException | InvalidProtocolBufferException | ValidatorConnectionError exc) {
      LOGGER.warning(exc.getMessage());
    }
    return null;
  }

}
