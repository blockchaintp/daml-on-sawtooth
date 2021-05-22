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

import com.blockchaintp.sawtooth.timekeeper.exceptions.TimeKeeperException;
import com.blockchaintp.sawtooth.timekeeper.protobuf.TimeKeeperRecord;
import com.blockchaintp.sawtooth.timekeeper.protobuf.TimeKeeperUpdate;
import com.blockchaintp.sawtooth.timekeeper.protobuf.TimeKeeperVersion;
import com.blockchaintp.sawtooth.timekeeper.protobuf.TimeKeeperRecord.Builder;
import com.google.protobuf.Timestamp;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the time values of a given participant.
 */
public final class ParticipantTimeState {

  /**
   * Default maximum history to keep.
   */
  private static final int DEFAULT_MAX_HISTORY = 100;

  private static final int DEFAULT_MAX_DEVIATION = 200;

  private Timestamp currentTime;

  private List<Timestamp> history;

  private TimeKeeperVersion version;

  private int maxHistory;

  private int maxDeviation;

  /**
   * Create a new ParticipantTimeState based on the provided record.
   *
   * @param tkRecord the record of this participant
   */
  public ParticipantTimeState(final TimeKeeperRecord tkRecord) {
    this.history = new ArrayList<>();
    this.currentTime = tkRecord.getLastCalculatedTime();
    history.addAll(tkRecord.getTimeHistoryList());
    this.version = tkRecord.getVersion();
    this.maxDeviation = DEFAULT_MAX_DEVIATION;
    this.maxHistory = DEFAULT_MAX_HISTORY;
  }

  /**
   * Create an initial state from an individual TimeKeeperUpdate.
   *
   * @param update the update to initialize with
   */
  public ParticipantTimeState(final TimeKeeperUpdate update) {
    currentTime = update.getTimeUpdate();
    history = new ArrayList<>(List.of(update.getTimeUpdate()));
    this.version = update.getVersion();

    if (TimeKeeperVersion.V_1_0.equals(this.version)) {
      this.maxHistory = DEFAULT_MAX_HISTORY;
      this.maxDeviation = DEFAULT_MAX_DEVIATION;
    } else {
      this.maxHistory = update.getMaxHistory();
      this.maxDeviation = update.getMaxDeviation();
    }
  }

  /**
   * Update this time state with and update from this participant.
   *
   * @param update the update
   * @throws TimeKeeperException when an incorrect style of update is sent to this
   * ParticipantTimeState
   */
  public void addUpdate(final TimeKeeperUpdate update) throws TimeKeeperException {
    if (update.getVersion().equals(TimeKeeperVersion.V_1_0)) {
      // Then we have a TimeKeeperVersion.V_1_0 update
      if (!this.version.equals(TimeKeeperVersion.V_1_0)) {
        // a version one update to greater than version one record
        // This should throw an error, no backsteps
        throw new TimeKeeperException(
            String.format("Invalid update version=%s for record version=%s", update.getVersion(), this.version));
      }
    } else {
      // then this is other than TimeKeeperVersion.V_1_0 update
      if (this.version.equals(TimeKeeperVersion.V_1_0)) {
        // upgrade the record
        this.version = update.getVersion();
      }
      if (update.getMaxDeviation() > 0) {
        this.maxDeviation = update.getMaxDeviation();
      }
      if (update.getMaxHistory() > 0) {
        this.maxHistory = update.getMaxHistory();
      }
    }
    history.add(update.getTimeUpdate());
    currentTime = TimestampUtils.max(currentTime, history);
    pruneHistory();

  }

  private void pruneHistory() {
    if (history.size() > maxHistory) {
      history = history.subList(history.size() - maxHistory, history.size());
    }
  }

  /**
   * Reify this state as a TimeKeeperRecord.
   *
   * @return the record
   */
  public TimeKeeperRecord toTimeKeeperRecord() {
    Builder builder = TimeKeeperRecord.newBuilder().setLastCalculatedTime(currentTime).addAllTimeHistory(history);
    if (!version.equals(TimeKeeperVersion.V_1_0)) {
      builder = builder.setVersion(this.version);
      if (maxDeviation != DEFAULT_MAX_DEVIATION) {
        builder = builder.setMaxDeviation(maxDeviation);
      }
      if (maxHistory != DEFAULT_MAX_HISTORY) {
        builder = builder.setMaxHistory(maxHistory);
      }
    }
    return builder.build();
  }
}
