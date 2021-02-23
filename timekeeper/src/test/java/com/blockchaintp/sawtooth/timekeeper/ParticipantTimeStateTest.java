package com.blockchaintp.sawtooth.timekeeper;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import com.blockchaintp.sawtooth.timekeeper.exceptions.TimeKeeperException;
import com.blockchaintp.sawtooth.timekeeper.processor.ParticipantTimeState;
import com.blockchaintp.sawtooth.timekeeper.protobuf.TimeKeeperRecord;
import com.blockchaintp.sawtooth.timekeeper.protobuf.TimeKeeperUpdate;
import com.blockchaintp.sawtooth.timekeeper.protobuf.TimeKeeperVersion;
import com.google.protobuf.util.Timestamps;

import org.junit.Test;

public class ParticipantTimeStateTest {

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
      TimeKeeperUpdate u = TimeKeeperUpdate.newBuilder().setTimeUpdate(Timestamps.fromSeconds(time)).setVersion(TimeKeeperVersion.V_2_0)
          .setMaxDeviation(deviation).setMaxHistory(history).build();
      retList.add(u);
    }
    return retList;
  }

  @Test
  public void testV1Consistent() throws TimeKeeperException {
    List<Long> test = new ArrayList<>();
    long max=80L;
    for (int i = 0; i < 200; i++) {
      max+=20L;
      test.add(max);
    }
    List<TimeKeeperUpdate> updates = v1updates(test.toArray(new Long[] {}));
    boolean first = true;
    ParticipantTimeState state = null;
    for (TimeKeeperUpdate u : updates) {
      if (first) {
        state = new ParticipantTimeState(u);
        first = false;
      } else {
        state.addUpdate(u);
      }
      TimeKeeperRecord tkr=state.toTimeKeeperRecord();
      long calcTime=tkr.getLastCalculatedTime().getSeconds();
      long uTime=u.getTimeUpdate().getSeconds();
      assertTrue(String.format("Unexpected currentTime %s!=%s", calcTime,uTime),
          calcTime == uTime);
      assertTrue(String.format("History size %s > 100", tkr.getTimeHistoryList().size()),
        tkr.getTimeHistoryList().size() <= 100);
      assertTrue(tkr.getVersion().equals(TimeKeeperVersion.V_1_0));
      assertTrue(tkr.getMaxDeviation() == 0);
      assertTrue(tkr.getMaxHistory() == 0);
    }

    updates = v1updates(test.toArray(new Long[] {}));
    for (TimeKeeperUpdate u : updates) {
      state.addUpdate(u);
      TimeKeeperRecord tkr=state.toTimeKeeperRecord();
      long calcTime=tkr.getLastCalculatedTime().getSeconds();
      assertTrue(String.format("Unexpected currentTime %s!=%s", calcTime,max),
          calcTime == max);
    }
  }

  @Test
  public void testV2Consistent() throws TimeKeeperException {
    List<Long> test = new ArrayList<>();
    long max=80L;
    for (int i = 0; i < 200; i++) {
      max+=20L;
      test.add(max);
    }
    List<TimeKeeperUpdate> updates = v2updates(0,0,test.toArray(new Long[] {}));
    boolean first = true;
    ParticipantTimeState state = null;
    for (TimeKeeperUpdate u : updates) {
      if (first) {
        state = new ParticipantTimeState(u);
        first = false;
      } else {
        state.addUpdate(u);
      }
      TimeKeeperRecord tkr=state.toTimeKeeperRecord();
      long calcTime=tkr.getLastCalculatedTime().getSeconds();
      long uTime=u.getTimeUpdate().getSeconds();
      assertTrue(String.format("Unexpected currentTime %s!=%s", calcTime,uTime),
          calcTime == uTime);
      assertTrue(String.format("History size %s > 10", tkr.getTimeHistoryList().size()),
        tkr.getTimeHistoryList().size() <= 10);
      assertTrue(tkr.getVersion().equals(TimeKeeperVersion.V_2_0));
      assertTrue(String.format("Max deviation %s!=%s",tkr.getMaxDeviation(),0),
        tkr.getMaxDeviation() == 0);
      assertTrue(String.format("Max history %s!=%s",tkr.getMaxHistory(),0),
        tkr.getMaxHistory() == 0);
    }

    updates = v2updates(20,20,test.toArray(new Long[] {}));
    for (TimeKeeperUpdate u : updates) {
      state.addUpdate(u);
      TimeKeeperRecord tkr=state.toTimeKeeperRecord();
      long calcTime=tkr.getLastCalculatedTime().getSeconds();
      assertTrue(String.format("Unexpected currentTime %s!=%s", calcTime,max),
          calcTime == max);
      assertTrue(tkr.getMaxDeviation() == 20);
      assertTrue(tkr.getMaxHistory() == 20);
    }

    updates = v1updates(test.toArray(new Long[] {}));
    for (TimeKeeperUpdate u : updates) {
      try {
        state.addUpdate(u);
        assertTrue("V2 should reject a v1 update",false);
      } catch (TimeKeeperException e) {}
    }
  }

  @Test
  public void testChangeOver() throws TimeKeeperException {
    long max = 80L;
    List<Long> testV1 = new ArrayList<>();
    for (int i = 0; i < 200; i++) {
      max+=20L;
      testV1.add(max);
    }
    List<TimeKeeperUpdate> updatesV1 = v1updates(testV1.toArray(new Long[] {}));

    boolean first = true;
    ParticipantTimeState state = null;
    for (TimeKeeperUpdate u : updatesV1) {
      if (first) {
        state = new ParticipantTimeState(u);
        first = false;
      } else {
        state.addUpdate(u);
      }
      TimeKeeperRecord tkr=state.toTimeKeeperRecord();
      long calcTime=tkr.getLastCalculatedTime().getSeconds();
      long uTime=u.getTimeUpdate().getSeconds();
      assertTrue(String.format("Unexpected currentTime %s!=%s", calcTime,uTime),
          calcTime == uTime);
      assertTrue(String.format("History size %s > 10", tkr.getTimeHistoryList().size()),
        tkr.getTimeHistoryList().size() <= 100);
      assertTrue(tkr.getVersion().equals(TimeKeeperVersion.V_1_0));
      assertTrue(String.format("Max deviation %s!=%s",tkr.getMaxDeviation(),0),
        tkr.getMaxDeviation() == 0);
      assertTrue(tkr.getMaxHistory() == 0);
    }

    List<Long> testV2 = new ArrayList<>();
    for (int i = 0; i < 200; i++) {
      max+=20L;
      testV2.add(max);
    }
    List<TimeKeeperUpdate> updatesV2 = v2updates(10,10,testV2.toArray(new Long[] {}));

    for (TimeKeeperUpdate u : updatesV2) {
      state.addUpdate(u);
      TimeKeeperRecord tkr=state.toTimeKeeperRecord();
      long calcTime=tkr.getLastCalculatedTime().getSeconds();
      long uTime=u.getTimeUpdate().getSeconds();
      assertTrue(String.format("Unexpected currentTime %s!=%s", calcTime,uTime),
          calcTime == uTime);
      assertTrue(tkr.getVersion().equals(TimeKeeperVersion.V_2_0));
      assertTrue(String.format("History size %s > 10", tkr.getTimeHistoryList().size()),
        tkr.getTimeHistoryList().size() <= 10);
      assertTrue(String.format("Max deviation %s!=%s",tkr.getMaxDeviation(),0),
        tkr.getMaxDeviation() == 10);
      assertTrue(String.format("Max history %s!=%s",tkr.getMaxHistory(),0),
        tkr.getMaxHistory() == 10);

    }

    for (TimeKeeperUpdate u : updatesV1) {
      try {
        state.addUpdate(u);
        assertTrue("V2 should reject a v1 update",false);
      } catch (TimeKeeperException e) {}
    }
  }
}
