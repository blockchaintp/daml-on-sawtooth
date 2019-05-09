package com.blockchaintp.sawtooth.timekeeper.util;

/**
 * Constants relating to Event subscription and attributes.
 */
public final class EventConstants {
  /**
   * Events of type timeKeeper will have this attribute set.
   */
  public static final String TIMEKEEPER_MICROS_ATTRIBUTE = "micros";

  /**
   * Event type for DamlLogEntry events.
   */
  public static final String TIMEKEEPER_EVENT_SUBJECT = Namespace.TIMEKEEPER_FAMILY_NAME + "/timeKeeper";

  private EventConstants() {

  }
}
