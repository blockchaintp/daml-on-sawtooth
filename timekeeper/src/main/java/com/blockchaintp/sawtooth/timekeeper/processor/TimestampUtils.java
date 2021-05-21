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

import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A collection of utilities for dealing with Timestamps.
 */
public final class TimestampUtils {

  /**
   * Return the maximum timestamp from a list.
   * @param timestamps the list
   * @return the maximum
   */
  public static Timestamp max(final List<Timestamp> timestamps) {
    return max(Timestamps.EPOCH, timestamps);
  }

  /**
   * Return the maximum timestamp given a previous timestamp and a list of
   * timestamps.
   *
   * @param currentMax the current timestamp
   * @param timestamps the list of timestamps
   * @return the maximum timestamp
   */
  public static Timestamp max(final Timestamp currentMax, final List<Timestamp> timestamps) {
    long maxSeconds = currentMax.getSeconds();
    for (final Timestamp ts : timestamps) {
      final long seconds = ts.getSeconds();
      maxSeconds = Math.max(maxSeconds, seconds);
    }
    return Timestamp.newBuilder().setSeconds(maxSeconds).build();
  }

  /**
   * Return the median of a list of timestamps.
   *
   * @param timestamps the timestamps
   * @return the median
   */
  public static Timestamp median(final Collection<Timestamp> timestamps) {
    long medianValue;
    final List<Long> sortedTs = timestamps.stream().map(t -> t.getSeconds()).sorted().collect(Collectors.toList());
    if (sortedTs.size() == 0) {
      return Timestamps.EPOCH;
    } else if (sortedTs.size() == 1) {
      return Timestamp.newBuilder().setSeconds(sortedTs.get(0)).build();
    } else if (sortedTs.size() % 2 == 0) {
      // find the mid point between size/2 and (size/2)+1
      final int half = (int) (sortedTs.size() / 2);
      final long low = sortedTs.get(half - 1);
      final long high = sortedTs.get(half);
      medianValue = (low + high) / 2;
    } else {
      // index should be ceiling of size/2
      final int midpoint = (int) Math.ceil((double) sortedTs.size() / (double) 2);
      medianValue = sortedTs.get(midpoint);
    }
    return Timestamp.newBuilder().setSeconds(medianValue).build();
  }

  /**
   * Return the median of a list of timestamps or the last timestamp, whichever is
   * greater.
   *
   * @param last       the last timestamp
   * @param timestamps the list of timestamps
   * @return the calculated timestamp
   */
  public static Timestamp medianOrLast(final Timestamp last, final Collection<Timestamp> timestamps) {
    final Timestamp median = median(timestamps);
    final long maxSec = Math.max(last.getSeconds(), median.getSeconds());
    return Timestamp.newBuilder().setSeconds(maxSec).build();
  }

  private TimestampUtils() {
  }
}
