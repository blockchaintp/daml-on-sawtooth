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
