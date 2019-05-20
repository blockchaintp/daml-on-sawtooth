package com.blockchaintp.sawtooth.daml.rpc.events;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;

import org.junit.Test;
import org.reactivestreams.Publisher;
import org.zeromq.ZMQ;

import com.blockchaintp.sawtooth.daml.util.EventConstants;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntry;
import com.daml.ledger.participant.state.v1.Offset;
import com.daml.ledger.participant.state.v1.Update;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import akka.NotUsed;
import akka.stream.scaladsl.Source;
import io.reactivex.subscribers.TestSubscriber;
import net.bytebuddy.utility.RandomString;
import sawtooth.sdk.protobuf.Event;
import sawtooth.sdk.protobuf.Event.Attribute;
import sawtooth.sdk.protobuf.EventList;
import sawtooth.sdk.protobuf.Message;
import sawtooth.sdk.protobuf.Message.MessageType;
import scala.Tuple2;

public class DamlLogEventHandlerTest {

  @Test
  public void testGetPublisher() {
    ZMQDelegate delegate = mock(ZMQDelegate.class);
    DamlLogEventHandler dleh = new DamlLogEventHandler(delegate);
    assertTrue(dleh.getPublisher() instanceof Publisher);
  }

  @Test
  public void testSendSubsribe() {
    ZMQDelegate delegate = mock(ZMQDelegate.class);
    DamlLogEventHandler dleh = new DamlLogEventHandler(delegate);
    dleh.sendSubscribe();
    doNothing().when(delegate).sendMessage(any());
    verify(delegate, times(1)).sendMessage(any());
  }

  @Test
  public void testSendUnsubscribe() {
    ZMQDelegate delegate = mock(ZMQDelegate.class);
    DamlLogEventHandler dleh = new DamlLogEventHandler(delegate);
    dleh.sendUnsubscribe();
    doNothing().when(delegate).sendMessage(any());
    verify(delegate, times(1)).sendMessage(any());
  }

  @Test
  public void testProcessMessage() {
    ZMQDelegate delegate = mock(ZMQDelegate.class);
    LogEntryTransformer transformer = mock(LogEntryTransformer.class);
    DamlLogEventHandler dleh = new DamlLogEventHandler(delegate, new ArrayList<String>(), transformer);

    String rndLogId = RandomString.make(10);
    Attribute blockNumAttr = Attribute.newBuilder().setKey(EventConstants.SAWTOOTH_BLOCK_NUM_EVENT_ATTRIBUTE)
        .setValue("9999").build();
    Attribute damlLogEntryIdAttr = Attribute.newBuilder().setKey(EventConstants.DAML_LOG_ENTRY_ID_EVENT_ATTRIBUTE)
        .setValue(ByteString.copyFromUtf8(rndLogId).toString()).build();
    DamlLogEntry defEntry = DamlLogEntry.getDefaultInstance();
    Event damlLogEvent = Event.newBuilder().addAttributes(damlLogEntryIdAttr).setData(defEntry.toByteString())
        .setEventType(EventConstants.DAML_LOG_EVENT_SUBJECT).build();
    Event blockCommitEvt = Event.newBuilder().addAttributes(blockNumAttr)
        .setEventType(EventConstants.SAWTOOTH_BLOCK_COMMIT_SUBJECT).build();

    EventList goodEvents = EventList.newBuilder().addEvents(blockCommitEvt).addEvents(damlLogEvent).build();

    Message goodMessage = Message.newBuilder().setContent(goodEvents.toByteString())
        .setMessageType(MessageType.CLIENT_EVENTS).build();
    try {

      Source<Tuple2<Offset, Update>, NotUsed> source = Source.fromPublisher(dleh.getPublisher());
      TestSubscriber<Tuple2<Offset, Update>> testSubscriber = new TestSubscriber<>();
      dleh.getPublisher().subscribe(testSubscriber);
      testSubscriber.assertSubscribed();
      testSubscriber.assertEmpty();
      dleh.processMessage(goodMessage);
      testSubscriber.assertNotComplete();
      testSubscriber.assertNoErrors();
      verify(transformer, times(1)).logEntryUpdate(any(), any());
    } catch (InvalidProtocolBufferException exc) {
      fail("A vallid message was sent but something went wrong with parsing");
    }

    Message notMyMessage = Message.newBuilder().setContent(goodEvents.toByteString())
        .setMessageType(MessageType.CLIENT_BATCH_GET_RESPONSE).build();
    try {

      Source<Tuple2<Offset, Update>, NotUsed> source = Source.fromPublisher(dleh.getPublisher());
      TestSubscriber<Tuple2<Offset, Update>> testSubscriber = new TestSubscriber<>();
      dleh = new DamlLogEventHandler(delegate, new ArrayList<String>(), transformer);

      dleh.getPublisher().subscribe(testSubscriber);
      testSubscriber.assertSubscribed();
      testSubscriber.assertEmpty();
      dleh.processMessage(notMyMessage);
      testSubscriber.assertNotComplete();
      testSubscriber.assertNoErrors();
      testSubscriber.assertEmpty();
      verify(transformer, times(1)).logEntryUpdate(any(), any());
    } catch (InvalidProtocolBufferException exc) {
      fail("A vallid message was sent but something went wrong with parsing");
    }
  }

  @Test
  public void testRun() {
    ZMQDelegate delegate = mock(ZMQDelegate.class);
    LogEntryTransformer transformer = mock(LogEntryTransformer.class);
    DamlLogEventHandler dleh = new DamlLogEventHandler(delegate, new ArrayList<String>(), transformer);

    when(delegate.monitorRecv()).thenReturn(new ZMQ.Event(ZMQ.EVENT_DISCONNECTED, new Object(), "address"));
    TestSubscriber<Tuple2<Offset, Update>> ts = new TestSubscriber<>();
    dleh.getPublisher().subscribe(ts);
    ts.assertEmpty();
    ts.assertNotComplete();
    dleh.run();
    ts.assertComplete();
  }
}
