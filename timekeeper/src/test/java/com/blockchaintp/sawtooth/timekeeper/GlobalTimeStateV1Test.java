package com.blockchaintp.sawtooth.timekeeper;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import com.blockchaintp.sawtooth.timekeeper.processor.GlobalTimeState;
import com.blockchaintp.sawtooth.timekeeper.protobuf.TimeKeeperGlobalRecord;
import com.blockchaintp.sawtooth.timekeeper.protobuf.TimeKeeperUpdate;
import com.blockchaintp.sawtooth.timekeeper.protobuf.TimeKeeperVersion;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.Timestamps;

import org.junit.Test;

public class GlobalTimeStateV1Test {

  List<TimeKeeperUpdate> v1updates(Long... times) {
    List<TimeKeeperUpdate> retList = new ArrayList<>();
    for (Long time : times) {
      TimeKeeperUpdate u = TimeKeeperUpdate.newBuilder().setTimeUpdate(Timestamps.fromSeconds(time)).build();
      retList.add(u);
    }
    return retList;
  }

  List<TimeKeeperUpdate> v2updates(int deviation, int history, Long... times) {
    List<TimeKeeperUpdate> retList = new ArrayList<>();
    for (Long time : times) {
      TimeKeeperUpdate u = TimeKeeperUpdate.newBuilder().setTimeUpdate(Timestamps.fromSeconds(time))
          .setVersion(TimeKeeperVersion.V_2_0).setMaxDeviation(deviation).setMaxHistory(history).build();
      retList.add(u);
    }
    return retList;
  }

  @Test
  public void testV1Consistent() throws InvalidProtocolBufferException {
    List<Long> test = new ArrayList<>();
    long max=80L;
    for (int i = 0; i < 2000; i++) {
      max+=20L;
      test.add(max);
    }
    List<TimeKeeperUpdate> v1TestUpdates = v1updates(test.toArray(new Long[] {}));

    List<ByteString> participants = new ArrayList<>();
    for ( int i = 0; i < 10; i++ ) {
      participants.add(ByteString.copyFrom("participant-"+i, Charset.defaultCharset()));
    }

    GlobalTimeState gState = new GlobalTimeState();
    int count=0;
    for (TimeKeeperUpdate u: v1TestUpdates) {
      int pIndex = count % participants.size();
      count++;
      gState.addUpdate(participants.get(pIndex), u);
      // Global State History v1 grows forever which is a problem but needs to stay for now
      if (count > (participants.size()* GlobalTimeState.MAX_TIME_HISTORY)) {
        assertTrue(String.format("%s < %s", gState.toTimeKeeperGlobalRecord().getTimeHistoryCount(),GlobalTimeState.MAX_TIME_HISTORY),
          gState.toTimeKeeperGlobalRecord().getTimeHistoryCount()>GlobalTimeState.MAX_TIME_HISTORY);
      }
    }

    test.clear();
    for (int i = 0; i < 2000; i++) {
      max+=20L;
      test.add(max);
    }
    v1TestUpdates = v1updates(test.toArray(new Long[] {}));

    count=0;
    for (TimeKeeperUpdate u: v1TestUpdates) {
      int pIndex = count % participants.size();
      count++;
      // Skip updates from the first so that it falls behind
      if (pIndex == 0) {
        continue;
      }

      gState.addUpdate(participants.get(pIndex), u);
      // If count > 10 then the skew should be greater than 200s and a
      // participant should drop oout
      if (count > 10) {
        assertTrue(String.format(" %s >= 10", gState.toTimeKeeperGlobalRecord().getParticipantCount()),
          gState.toTimeKeeperGlobalRecord().getParticipantCount() < 10 );
      }
    }
    TimeKeeperGlobalRecord tkgr = gState.toTimeKeeperGlobalRecord();
    assertEquals(80020, tkgr.getLastCalculatedTime().getSeconds());
    assertEquals(TimeKeeperVersion.V_1_0, tkgr.getVersion());
    assertEquals(9, tkgr.getParticipantCount());
  }

  @Test
  public void testV12Upgrade() throws InvalidProtocolBufferException {
    List<Long> test = new ArrayList<>();
    long max=80L;
    for (int i = 0; i < 2000; i++) {
      max+=20L;
      test.add(max);
    }
    List<TimeKeeperUpdate> v1TestUpdates = v1updates(test.toArray(new Long[] {}));

    List<ByteString> participants = new ArrayList<>();
    for ( int i = 0; i < 10; i++ ) {
      participants.add(ByteString.copyFrom("participant-"+i, Charset.defaultCharset()));
    }

    GlobalTimeState gState = new GlobalTimeState();
    int count=0;
    for (TimeKeeperUpdate u: v1TestUpdates) {
      int pIndex = count % participants.size();
      count++;
      gState.addUpdate(participants.get(pIndex), u);
      // Global State History v1 grows forever which is a problem but needs to stay for now
      if (count > (participants.size()* GlobalTimeState.MAX_TIME_HISTORY)) {
        assertTrue(String.format("%s < %s", gState.toTimeKeeperGlobalRecord().getTimeHistoryCount(),GlobalTimeState.MAX_TIME_HISTORY),
          gState.toTimeKeeperGlobalRecord().getTimeHistoryCount()>GlobalTimeState.MAX_TIME_HISTORY);
      }
    }

    for (int i = 0; i < 2000; i++) {
      max+=20L;
      test.add(max);
    }
    List<TimeKeeperUpdate> v2TestUpdates = v2updates(200, 100, test.toArray(new Long[] {}));

    count=0;
    for (TimeKeeperUpdate u: v2TestUpdates) {
      int pIndex = count % participants.size();
      count++;
      gState.addUpdate(participants.get(pIndex), u);
      // v2 should not keep history in global state
      TimeKeeperGlobalRecord tkgr=gState.toTimeKeeperGlobalRecord();
      assertEquals(TimeKeeperVersion.V_2_0, tkgr.getVersion());
      assertEquals(0, tkgr.getTimeHistoryCount());
      //
    }


  }

}
