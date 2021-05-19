/* Copyright 2019-21 Blockchain Technology Partners
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

package com.blockchaintp.sawtooth.timekeeper.processor;

import com.blockchaintp.sawtooth.timekeeper.protobuf.TimeKeeperGlobalRecord;
import com.blockchaintp.sawtooth.timekeeper.protobuf.TimeKeeperGlobalRecord.Builder;
import com.blockchaintp.sawtooth.timekeeper.protobuf.TimeKeeperParticipant;
import com.blockchaintp.sawtooth.timekeeper.protobuf.TimeKeeperUpdate;
import com.blockchaintp.sawtooth.timekeeper.protobuf.TimeKeeperVersion;
import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Encapsulates and anages the calculations fot TimekeeperGlobalRecords.
 */
public final class GlobalTimeState {

  private static final int LEGACY_DEFAULT_UPDATE_PERIOD = 20;

  private static final Logger LOGGER = LoggerFactory.getLogger(GlobalTimeState.class);

  /**
   * Maxiumum number of history entries to keep.
   */
  public static final int MAX_TIME_HISTORY = 100;

  /**
   * Number of update intervals back to retain participant times.
   */
  private static final int PERIOD_FLOOR = 10;

  private Timestamp currentTime;
  private List<Timestamp> history;
  private Map<ByteString, Timestamp> participantTimes;
  private TimeKeeperVersion version;

  /**
   * Create a new global time state object and initialize with the provided
   * record.
   *
   * @param record the current TimeKeeperGlobal record
   */
  public GlobalTimeState(final TimeKeeperGlobalRecord record) {
    this();
    this.currentTime = record.getLastCalculatedTime();
    this.history.addAll(record.getTimeHistoryList());
    this.version = record.getVersion();
    for (final TimeKeeperParticipant p : record.getParticipantList()) {
      participantTimes.put(p.getParticipantPublicKey(), p.getLastCalculatedTime());
    }
    // do nothing else as this TimeKeeperRecord is presumed to already be
    // incorporated into the TimeKeeperGlobalRecord
  }

  /**
   * Create a new global timestate object from the beginning of EPOCH.
   */
  public GlobalTimeState() {
    this.currentTime = Timestamps.EPOCH;
    this.history = new ArrayList<>();
    this.participantTimes = new HashMap<>();
    this.version = TimeKeeperVersion.V_1_0;
  }

  /**
   * Update this time state with and update from this participant.
   *
   * @param participant the participant public identifier
   * @param update      the update
   */
  public void addUpdate(final ByteString participant, final TimeKeeperUpdate update) {
    if (update.getVersion().equals(TimeKeeperVersion.V_2_0)) {
      this.version = TimeKeeperVersion.V_2_0;
    }
    addUpdate(participant, update.getTimeUpdate());
  }

  /**
   * Update this time state with and update from this participant.
   *
   * @param participant the participant public identifier
   * @param update      the timestamp to use for update
   */
  public void addUpdate(final ByteString participant, final Timestamp update) {
    if (!participantTimes.containsKey(participant)) {
      LOGGER.info("New TimeKeeper particpant detected {}", participant.toStringUtf8());
    }
    Timestamp prevPartTime = participantTimes.getOrDefault(participant, Timestamps.EPOCH);

    Timestamp newTime = TimestampUtils.max(prevPartTime, List.of(update));
    if (newTime.getSeconds() != prevPartTime.getSeconds()) {
      LOGGER.debug("Particpant {} new time={}", participant.toStringUtf8(), new Date(Timestamps.toMillis(newTime)));
    }
    participantTimes.put(participant, newTime);
    pruneExpiredParticipants(participantTimes);
    final Timestamp previousTime = currentTime;
    currentTime = TimestampUtils.medianOrLast(currentTime, participantTimes.values());
    if (previousTime.getSeconds() != currentTime.getSeconds()) {
      history.add(currentTime);
      if (history.size() > MAX_TIME_HISTORY) {
        history.subList(history.size() - MAX_TIME_HISTORY, history.size());
      }
    }

  }

  private void pruneExpiredParticipants(final Map<ByteString, Timestamp> participants) {
    final List<ByteString> toRemove = new ArrayList<>();
    final long currentSeconds = currentTime.getSeconds();
    final long bottomThreshold = currentSeconds - (PERIOD_FLOOR * LEGACY_DEFAULT_UPDATE_PERIOD);

    for (final Map.Entry<ByteString, Timestamp> e : participants.entrySet()) {
      final long seconds = e.getValue().getSeconds();
      if (seconds <= bottomThreshold) {
        toRemove.add(e.getKey());
      }
    }
    for (final ByteString k : toRemove) {
      participants.remove(k);
    }
  }

  /**
   * Reify this state into a TimeKeeperGlobalRecord.
   *
   * @return a record representing the current state of this object
   */
  public TimeKeeperGlobalRecord toTimeKeeperGlobalRecord() {
    final Builder builder = TimeKeeperGlobalRecord.newBuilder().setLastCalculatedTime(currentTime);
    if (this.version.equals(TimeKeeperVersion.V_1_0)) {
      builder.addAllTimeHistory(history);
    }
    for (final Map.Entry<ByteString, Timestamp> e : participantTimes.entrySet()) {
      final TimeKeeperParticipant part = TimeKeeperParticipant.newBuilder().setParticipantPublicKey(e.getKey())
          .setLastCalculatedTime(e.getValue()).build();
      builder.addParticipant(part);
    }
    builder.setVersion(this.version);
    return builder.build();
  }

  /**
   * Return the curent time.
   *
   * @return the currentTime
   */
  public Timestamp getCurrentTime() {
    return currentTime;
  }
}
