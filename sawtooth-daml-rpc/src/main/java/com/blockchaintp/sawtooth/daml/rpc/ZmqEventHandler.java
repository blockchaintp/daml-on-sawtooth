/*
 * Copyright 2019 Blockchain Technology Partners Licensed under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable
 * law or agreed to in writing, software distributed under the License is distributed on an "AS IS"
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under the License.
 * ------------------------------------------------------------------------------
 */
package com.blockchaintp.sawtooth.daml.rpc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import com.blockchaintp.utils.SawtoothClientUtils;
import com.blockchaintp.sawtooth.daml.EventConstants;
import com.blockchaintp.sawtooth.messaging.ZmqStream;
import com.daml.ledger.participant.state.kvutils.KVOffset;
import com.daml.ledger.participant.state.kvutils.api.LedgerRecord;
import com.daml.ledger.participant.state.v1.Offset;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZFrame;
import org.zeromq.ZLoop;
import org.zeromq.ZMQ.PollItem;
import org.zeromq.ZMsg;
import io.reactivex.processors.UnicastProcessor;
import sawtooth.sdk.messaging.Future;
import sawtooth.sdk.messaging.Stream;
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

/**
 * A thread which subscribes to sawtooth events, receives those events and provides them to a
 * Processor.
 */
public class ZmqEventHandler implements Runnable, ZLoop.IZLoopHandler {

  private static final int DEFAULT_TIMEOUT = 10;
  private static final Logger LOGGER = LoggerFactory.getLogger(ZmqEventHandler.class);
  private static final String[] SUBSCRIBE_SUBJECTS = new String[] {
      EventConstants.SAWTOOTH_BLOCK_COMMIT_SUBJECT, EventConstants.DAML_LOG_EVENT_SUBJECT,
      com.blockchaintp.sawtooth.timekeeper.EventConstants.TIMEKEEPER_EVENT_SUBJECT};

  private final Collection<EventSubscription> subscriptions;

  private final List<UnicastProcessor<LedgerRecord>> processors;
  private boolean subscribed = false;
  private final Stream stream;
  private long currentBlockNum;
  private int currentSubOffset;

  /**
   * Build a handler for the given zmqUrl.
   *
   * @param zmqUrl the zmq address to connect to
   */
  public ZmqEventHandler(final String zmqUrl) {
    this(new ZmqStream(zmqUrl));
    LOGGER.info("Connecting to validator at {}", zmqUrl);
  }

  /**
   * Build a handler based on the given Stream.
   *
   * @param argStream the delegate to use
   */
  public ZmqEventHandler(final Stream argStream) {
    this.currentBlockNum = 0;
    this.subscriptions = new ArrayList<EventSubscription>();
    for (final String subject : SUBSCRIBE_SUBJECTS) {
      final EventSubscription evtSubscription =
          EventSubscription.newBuilder().setEventType(subject).build();
      this.subscriptions.add(evtSubscription);
    }
    this.stream = argStream;
    this.processors = Collections.synchronizedList(new ArrayList<>());
  }

  private String getBlockIdByOffset(final Offset offset) {
    // Block 1 is the genesis block, While unlikely the first possible interesting
    // data is at block 2
    final Long blockNum = Math.max(2, KVOffset.highestIndex(offset));

    final ClientBlockGetByNumRequest bgbn =
        ClientBlockGetByNumRequest.newBuilder().setBlockNum(blockNum).build();
    final Future resp =
        this.stream.send(MessageType.CLIENT_BLOCK_GET_BY_NUM_REQUEST, bgbn.toByteString());

    String retBlockId = null;
    LOGGER.debug("Waiting for ClientBlockGetResponse for block_num: {}", blockNum);
    try {
      ByteString result = null;
      while (result == null) {
        try {
          result = resp.getResult(DEFAULT_TIMEOUT);
          final ClientBlockGetResponse response = ClientBlockGetResponse.parseFrom(result);
          switch (response.getStatus()) {
            case OK:
              LOGGER.debug("ClientBlockGetResponse received...");
              retBlockId = response.getBlock().getHeaderSignature();
              break;
            case NO_RESOURCE:
              LOGGER.warn("NO_RESOURCE received from ClientBlockGetResponse: {}",
                  response.toString());
              return null;
            case INTERNAL_ERROR:
            case UNRECOGNIZED:
            case INVALID_ID:
            case STATUS_UNSET:
            default:
              LOGGER.warn("Invalid response received from ClientBlockGetByNumRequest: {}",
                      response.getStatus());
          }
        } catch (final TimeoutException exc) {
          LOGGER.warn("Still waiting for ClientBlockGetResponse...");
        }
      }
    } catch (InterruptedException | InvalidProtocolBufferException | ValidatorConnectionError exc) {
      LOGGER.warn(exc.getMessage());
    }
    return retBlockId;
  }

  private Map<String, String> eventAttributeMap(final Event evt) {
    final Map<String, String> attrMap = new HashMap<>();
    final List<Attribute> attributes = evt.getAttributesList();
    for (final Attribute attr : attributes) {
      attrMap.put(attr.getKey(), attr.getValue());
    }
    return attrMap;
  }

  /**
   * Make a publisher for the event subscription this handler controls.
   *
   * @return the publisher
   */
  public final Publisher<LedgerRecord> makePublisher() {
    final UnicastProcessor<LedgerRecord> p = UnicastProcessor.create();
    this.processors.add(p);
    return p;
  }

  @Override
  public final int handle(final ZLoop loop, final PollItem item, final Object arg) {
    LOGGER.debug("Handling message...");

    final ZMsg msg = ZMsg.recvMsg(item.getSocket());
    final Iterator<ZFrame> multiPartMessage = msg.iterator();

    while (multiPartMessage.hasNext()) {
      final ZFrame frame = multiPartMessage.next();
      try {
        final Message message = Message.parseFrom(frame.getData());
        processMessage(message);
      } catch (final IOException exc) {
        LOGGER.warn(exc.getMessage());
      }
    }
    return 0;
  }

  protected final void processMessage(final Message message) throws IOException {
    LOGGER.debug("Processing Message");
    if (message.getMessageType().equals(MessageType.CLIENT_EVENTS)) {
      handleClientEvents(message);
    }
  }

  private long getCurrentBlockNum() {
    return currentBlockNum;
  }

  private void setCurrentBlockNum(final long blockNum) {
    this.currentBlockNum = blockNum;
    this.currentSubOffset = 0;
  }

  private void incrementSubOffset() {
    this.currentSubOffset++;
  }

  private int getCurrentSubOffset() {
    return this.currentSubOffset;
  }

  private void handleClientEvents(final Message message) {
    try {
      LOGGER.trace("Handle client events");
      final EventList evtList = EventList.parseFrom(message.getContent());
      for (final Event e : evtList.getEventsList()) {
        LOGGER.trace("Received event of type {}", e.getEventType());
        final Map<String, String> attrMap = eventAttributeMap(e);
        switch (e.getEventType()) {
          case EventConstants.SAWTOOTH_BLOCK_COMMIT_SUBJECT:
            handleBlockCommitEvent(attrMap, e);
            break;
          case EventConstants.DAML_LOG_EVENT_SUBJECT:
            handleDamlLogEvent(attrMap, e);
            break;
          default:
            break;
        }
      }

    } catch (final InvalidProtocolBufferException e) {
      LOGGER.warn("InvalidProtocolBufferException parsing EventList");
    }
  }

  private void handleDamlLogEvent(final Map<String, String> attrMap, final Event e) {
    LOGGER.trace("Handling DAML log event");
    final String entryIdStr = attrMap.get(EventConstants.DAML_LOG_ENTRY_ID_EVENT_ATTRIBUTE);
    final ByteString entryIdVal = ByteString.copyFromUtf8(entryIdStr);
    final ByteString evtData = SawtoothClientUtils.unwrap(e.getData());
    final long blockNum = getCurrentBlockNum();
    final int subOffset = getCurrentSubOffset();
    final Offset eventOffset = KVOffset.fromLong(blockNum, subOffset, 0);
    incrementSubOffset();
    final LedgerRecord lr = LedgerRecord.apply(eventOffset, entryIdVal, evtData);
    sendToProcessors(lr);
  }

  private void sendToProcessors(final LedgerRecord lr) {
    for (final UnicastProcessor<LedgerRecord> p : this.processors) {
      p.onNext(lr);
    }
  }

  private void handleBlockCommitEvent(final Map<String, String> attrMap, final Event e) {
    final long blockNum =
        Long.parseLong(attrMap.get(EventConstants.SAWTOOTH_BLOCK_NUM_EVENT_ATTRIBUTE));
    setCurrentBlockNum(blockNum);
    LOGGER.debug("Received block-commit block_num={}", blockNum);
  }

  @Override
  public final void run() {
    while (true) {
      Message receivedMsg = null;
      try {
        receivedMsg = this.stream.receive(DEFAULT_TIMEOUT);
      } catch (final TimeoutException exc) {
        LOGGER.trace("Timeout waiting for message");
        receivedMsg = null;
      }
      if (receivedMsg != null) {
        try {
          processMessage(receivedMsg);
        } catch (final IOException exc) {
          LOGGER.warn("Error unmarshalling message of type: {}",
              receivedMsg.getMessageType());
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
      LOGGER.warn("Attempted to subscribe twice");
      return;
    }

    final List<String> lastBlockIds = new ArrayList<>();
    if (beginAfter != null) {
      final String blockId = getBlockIdByOffset(beginAfter);
      if (blockId != null) {
        lastBlockIds.add(blockId);
      }

    }

    final ClientEventsSubscribeRequest cesReq = ClientEventsSubscribeRequest.newBuilder()
        .addAllSubscriptions(subscriptions).addAllLastKnownBlockIds(lastBlockIds).build();

    final Future resp =
        this.stream.send(MessageType.CLIENT_EVENTS_SUBSCRIBE_REQUEST, cesReq.toByteString());
    LOGGER.debug("Waiting for subscription response...");
    try {
      ByteString result = null;
      while (result == null) {
        try {
          result = resp.getResult(DEFAULT_TIMEOUT);
          final ClientEventsSubscribeResponse subscribeResponse =
              ClientEventsSubscribeResponse.parseFrom(result);
          switch (subscribeResponse.getStatus()) {
            case UNKNOWN_BLOCK:
              throw new RuntimeException(
                  String.format("Unknown blockids in subscription: %s", lastBlockIds));
            case INVALID_FILTER:
              LOGGER.warn("InvalidFilters response received");
            case STATUS_UNSET:
            case OK:
              this.subscribed = true;
              LOGGER.info("Subscription response received...");
              return;
            default:
          }
        } catch (final TimeoutException exc) {
          LOGGER.warn("Still waiting for subscription response...");
        }
      }

    } catch (InterruptedException | InvalidProtocolBufferException | ValidatorConnectionError exc) {
      LOGGER.warn(exc.getMessage());
    }
  }

  /**
   * Send an unsubscribe message to the validator.
   */
  public final void sendUnsubscribe() {
    final ClientEventsUnsubscribeRequest ceuReq =
        ClientEventsUnsubscribeRequest.newBuilder().build();
    final Future resp =
        this.stream.send(MessageType.CLIENT_EVENTS_UNSUBSCRIBE_REQUEST, ceuReq.toByteString());
    try {
      resp.getResult();
      LOGGER.debug("Unsubscribed...");
    } catch (InterruptedException | ValidatorConnectionError exc) {
      LOGGER.warn(exc.getMessage());
    }
  }

  /**
   * Get the state at the given address.
   *
   * @param address the address of the state entry
   * @return the data at the address
   */
  public ByteString getState(final String address) {
    final ClientStateGetRequest req =
        ClientStateGetRequest.newBuilder().setAddress(address).build();
    final Future resp = stream.send(MessageType.CLIENT_STATE_GET_REQUEST, req.toByteString());

    LOGGER.debug("Waiting for ClientStateGetResponse for address {}", address);
    try {
      ByteString result = null;
      while (result == null) {
        try {
          result = resp.getResult(DEFAULT_TIMEOUT);
          final ClientStateGetResponse response = ClientStateGetResponse.parseFrom(result);
          switch (response.getStatus()) {
            case OK:
              final ByteString bs = response.getValue();
              LOGGER
                  .debug("ClientStateGetResponse received OK for {}={}", address, bs);
              return bs;
            case NO_RESOURCE:
              LOGGER.warn("Address {} not currently set", address);
              return null;
            case INVALID_ADDRESS:
            case NOT_READY:
            case NO_ROOT:
            case INTERNAL_ERROR:
            case UNRECOGNIZED:
            case STATUS_UNSET:
            case INVALID_ROOT:
            default:
              LOGGER.warn(
                  "Invalid response received from ClientStateGetRequest address={} response={}",
                  address, response.getStatus());
              return null;
          }
        } catch (final TimeoutException exc) {
          LOGGER.warn("Still waiting for ClientStateGetResponse address={}", address);
        }
      }
    } catch (InterruptedException | InvalidProtocolBufferException | ValidatorConnectionError exc) {
      LOGGER.warn(exc.getMessage());
    }
    return null;
  }

}
