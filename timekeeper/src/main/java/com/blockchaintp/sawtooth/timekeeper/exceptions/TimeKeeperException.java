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
package com.blockchaintp.sawtooth.timekeeper.exceptions;

/**
 * Represents exceptions in the TimeKeeper logic.
 */
public class TimeKeeperException extends Exception {

  private static final long serialVersionUID = 3437463937092554297L;

  /**
   * Construct exception from a throwable exception.
   *
   * @param throwable exception.
   */
  public TimeKeeperException(final Throwable throwable) {
    super(throwable);
  }

  /**
   * Construct exception from a message and a throwable exception.
   *
   * @param message describing the nature of the exception.
   * @param throwable exception.
   */
  public TimeKeeperException(final  String message, final Throwable throwable) {
    super(message, throwable);
  }

  /**
   * Construct an exception class with a defined message.
   *
   * @param message to be displayed.
   */
  public TimeKeeperException(final String message) {
    super(String.format("TimeKeeperException: %s", message));
  }
}
