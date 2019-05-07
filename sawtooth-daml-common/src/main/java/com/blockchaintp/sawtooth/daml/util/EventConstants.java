package com.blockchaintp.sawtooth.daml.util;

/**
 * Constants relating to Event subscription and attributes.
 *
 */
public final class EventConstants {
  /**
   * Events of type daml/logEvent will have this attribute set.
   */
  public static final String DAML_LOG_ENTRY_ID_EVENT_ATTRIBUTE = "logEntryId";

  /**
   * Event type for DamlLogEntry events.
   */
  public static final String DAML_LOG_EVENT_SUBJECT = Namespace.DAML_FAMILY_NAME + "/logEvent";

  /**
   * Attribute of the block number in a sawtooth/block-commit event.
   */
  public static final String SAWTOOTH_BLOCK_NUM_EVENT_ATTRIBUTE = "block_num";

  /**
   * Event type for block-commit events.
   */
  public static final String SAWTOOTH_BLOCK_COMMIT_SUBJECT = "sawtooth/block-commit";

  private EventConstants() {

  }
}
