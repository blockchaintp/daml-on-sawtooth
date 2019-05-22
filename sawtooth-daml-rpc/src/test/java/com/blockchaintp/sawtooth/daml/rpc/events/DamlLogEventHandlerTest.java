package com.blockchaintp.sawtooth.daml.rpc.events;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.Test;
import org.reactivestreams.Publisher;

import sawtooth.sdk.messaging.Stream;

public class DamlLogEventHandlerTest {

  @Test
  public void testGetPublisher() {
    Stream stream = mock(Stream.class);
    DamlLogEventHandler dleh = new DamlLogEventHandler(stream);
    assertTrue(dleh.getPublisher() instanceof Publisher);
  }

//  @Test
//  public void testSendSubsribe() {
//    Stream stream = mock(Stream.class);
//    DamlLogEventHandler dleh = new DamlLogEventHandler(stream);
//    dleh.sendSubscribe();
//    Future mockResp = mock(Future.class);
//    Message myMessage = Message.newBuilder().setMessageType(MessageType.CLIENT_EVENTS_SUBSCRIBE_RESPONSE).build();
//    try {
//      when(mockResp.getResult(any())).thenReturn(myMessage.toByteString());
//    } catch (InterruptedException | TimeoutException | ValidatorConnectionError exc) {
//      // TODO Auto-generated catch block
//      exc.printStackTrace();
//    }
//    when(stream.send(any(), any())).thenReturn(mock(Future.class));
//    verify(stream, times(1)).send(any(), any());
//  }
//
//  @Test
//  public void testSendUnsubscribe() {
//    Stream stream = mock(Stream.class);
//    DamlLogEventHandler dleh = new DamlLogEventHandler(stream);
//    dleh.sendUnsubscribe();
//    when(stream.send(any(), any())).thenReturn(mock(Future.class));
//    verify(stream, times(1)).send(any(), any());
//  }

//  @Test
//  public void testProcessMessage() {
//    Stream stream = mock(Stream.class);
//    LogEntryTransformer transformer = mock(LogEntryTransformer.class);
//    DamlLogEventHandler dleh = new DamlLogEventHandler(stream, new ArrayList<String>(), transformer);
//
//    String rndLogId = RandomString.make(10);
//    Attribute blockNumAttr = Attribute.newBuilder().setKey(EventConstants.SAWTOOTH_BLOCK_NUM_EVENT_ATTRIBUTE)
//        .setValue("9999").build();
//    Attribute damlLogEntryIdAttr = Attribute.newBuilder().setKey(EventConstants.DAML_LOG_ENTRY_ID_EVENT_ATTRIBUTE)
//        .setValue(ByteString.copyFromUtf8(rndLogId).toString()).build();
//    Attribute damlLogOffsetAttr = Attribute.newBuilder().setKey(EventConstants.DAML_OFFSET_EVENT_ATTRIBUTE)
//        .setValue("" + ThreadLocalRandom.current().nextLong()).build();
//
//    DamlLogEntry defEntry = DamlLogEntry.getDefaultInstance();
//    Event damlLogEvent = Event.newBuilder().addAttributes(damlLogEntryIdAttr).addAttributes(damlLogOffsetAttr)
//        .setData(defEntry.toByteString()).setEventType(EventConstants.DAML_LOG_EVENT_SUBJECT).build();
//    Event blockCommitEvt = Event.newBuilder().addAttributes(blockNumAttr)
//        .setEventType(EventConstants.SAWTOOTH_BLOCK_COMMIT_SUBJECT).build();
//
//    EventList goodEvents = EventList.newBuilder().addEvents(blockCommitEvt).addEvents(damlLogEvent).build();
//
//    Message goodMessage = Message.newBuilder().setContent(goodEvents.toByteString())
//        .setMessageType(MessageType.CLIENT_EVENTS).build();
//    try {
//
//      Source<Tuple2<Offset, Update>, NotUsed> source = Source.fromPublisher(dleh.getPublisher());
//      TestSubscriber<Tuple2<Offset, Update>> testSubscriber = new TestSubscriber<>();
//      dleh.getPublisher().subscribe(testSubscriber);
//      testSubscriber.assertSubscribed();
//      testSubscriber.assertEmpty();
//      dleh.processMessage(goodMessage);
//      testSubscriber.assertNotComplete();
//      testSubscriber.assertNoErrors();
//      verify(transformer, times(1)).logEntryUpdate(any(), any());
//    } catch (InvalidProtocolBufferException exc) {
//      fail("A vallid message was sent but something went wrong with parsing");
//    }
//
//    Message notMyMessage = Message.newBuilder().setContent(goodEvents.toByteString())
//        .setMessageType(MessageType.CLIENT_BATCH_GET_RESPONSE).build();
//    try {
//
//      Source<Tuple2<Offset, Update>, NotUsed> source = Source.fromPublisher(dleh.getPublisher());
//      TestSubscriber<Tuple2<Offset, Update>> testSubscriber = new TestSubscriber<>();
//      dleh = new DamlLogEventHandler(stream, new ArrayList<String>(), transformer);
//
//      dleh.getPublisher().subscribe(testSubscriber);
//      testSubscriber.assertSubscribed();
//      testSubscriber.assertEmpty();
//      dleh.processMessage(notMyMessage);
//      testSubscriber.assertNotComplete();
//      testSubscriber.assertNoErrors();
//      testSubscriber.assertEmpty();
//      verify(transformer, times(1)).logEntryUpdate(any(), any());
//    } catch (InvalidProtocolBufferException exc) {
//      fail("A vallid message was sent but something went wrong with parsing");
//    }
//  }
//
//  @Test
//  public void testRun() {
//    Stream stream = mock(Stream.class);
//    LogEntryTransformer transformer = mock(LogEntryTransformer.class);
//    DamlLogEventHandler dleh = new DamlLogEventHandler(stream, new ArrayList<String>(), transformer);
//    when(stream.send(any(), any())).thenReturn(mock(Future.class));
//
//    TestSubscriber<Tuple2<Offset, Update>> ts = new TestSubscriber<>();
//    dleh.getPublisher().subscribe(ts);
//    ts.assertEmpty();
//    ts.assertNotComplete();
//    dleh.run();
//    ts.assertComplete();
//  }
}
