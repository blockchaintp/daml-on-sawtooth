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

  /**
   * Attribute of the current value of the offset counter for this event.
   */
  public static final String DAML_OFFSET_EVENT_ATTRIBUTE = "offset-counter";

  public static final String DAML_LOG_ENTRY_ID_EVENT_PARTS = "event-part-list";

  public static final String DAML_LOG_ENTRY_ID_EVENT_PART = "event-part";

  private EventConstants() {

  }
}
