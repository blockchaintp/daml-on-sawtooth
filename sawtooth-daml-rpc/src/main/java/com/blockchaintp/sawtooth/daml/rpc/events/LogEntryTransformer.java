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
package com.blockchaintp.sawtooth.daml.rpc.events;

import java.util.List;

import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntry;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntryId;
import com.daml.ledger.participant.state.v1.Update;

/**
 * An interface to introduce a layer of indirection between the processing and
 * the implementation.
 * 
 * @author scealiontach
 */
public interface LogEntryTransformer {

  /**
   * Transform a log entry and its id into a single Update.
   * 
   * @param entryId  the id of the log entry
   * @param logEntry the entry itself
   * @return an Update suitable for publishing to Daml
   */
  List<Update> logEntryUpdate(DamlLogEntryId entryId, DamlLogEntry logEntry);

}
