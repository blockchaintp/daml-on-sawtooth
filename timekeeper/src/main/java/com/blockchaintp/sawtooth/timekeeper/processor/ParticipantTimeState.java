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

import com.blockchaintp.sawtooth.timekeeper.protobuf.TimeKeeperRecord;
import com.blockchaintp.sawtooth.timekeeper.protobuf.TimeKeeperUpdate;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;

import java.util.ArrayList;
import java.util.List;


public class ParticipantTimeState {

  private Timestamp currentTime;

  private List<Timestamp> history;

  /**
   * Maximum history to keep.
   */
  private static final int MAX_TIME_HISTORY = 100;

  /**
   * Create a new ParticipantTimeState based on the provided record.
   * @param record the record of this participant
   */
  public ParticipantTimeState(final TimeKeeperRecord record) {
    this();
    this.currentTime = record.getLastCalculatedTime();
    history.addAll(record.getTimeHistoryList());
  }

  public ParticipantTimeState() {
    currentTime = Timestamps.EPOCH;
    history = new ArrayList<>();
  }

  /**
   * Update this time state with and update from this participant.
   *
   * @param update      the update
   */
  public void addUpdate(final TimeKeeperUpdate update) {
    addUpdate(update.getTimeUpdate());
  }

  /**
   * Update this time state with and update from this participant.
   *
   * @param update      the timestamp to use for update
   */
  public void addUpdate(final Timestamp update) {
    history.add(update);
    if (history.size() > MAX_TIME_HISTORY) {
      history = history.subList(history.size() - MAX_TIME_HISTORY, history.size());
    }
    currentTime = TimestampUtils.max(currentTime, history);
  }

  /**
   * Reify this state as a TimeKeeperRecord.
   *
   * @return the record
   */
  public TimeKeeperRecord toTimeKeeperRecord() {
    return TimeKeeperRecord.newBuilder().setLastCalculatedTime(currentTime).addAllTimeHistory(history).build();
  }
}
