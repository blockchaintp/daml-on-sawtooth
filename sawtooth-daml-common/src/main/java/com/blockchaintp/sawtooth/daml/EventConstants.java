/*
 * Copyright © 2023 Paravela Limited Licensed under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable
 * law or agreed to in writing, software distributed under the License is distributed on an "AS IS"
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under the License.
 * ------------------------------------------------------------------------------
 */
package com.blockchaintp.sawtooth.daml;

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
   * Events of type daml/logEvent will have this attribute set. It contains the number of STL events
   * which make up this daml event.
   */
  public static final String DAML_LOG_ENTRY_ID_PART_COUNT_ATTRIBUTE = "logEntryPartCount";

  /**
   * Events of type daml/logEvent will have this attribute set. It contains the part number of this
   * chunk of the DAML event
   */
  public static final String DAML_LOG_ENTRY_ID_PART_ATTRIBUTE = "logEntryPart";

  /**
   * Events of type daml/logEvent which need to be fetched will have this attribute set. It contains
   * a comma separated list of addresses to fetch.
   */
  public static final String DAML_LOG_FETCH_IDS_ATTRIBUTE = "fetchEntryPart";

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

  /**
   * Attribute of the current value of the offset counter for this event.
   */
  public static final String DAML_OFFSET_EVENT_ATTRIBUTE = "offset-counter";

  /**
   * Attribute specifying that the content should be fetched from state "true" for yes, anything
   * else is no.
   */
  public static final String DAML_LOG_ENTRY_FETCH_ATTRIBUTE = "fetch";

  private EventConstants() {

  }
}
