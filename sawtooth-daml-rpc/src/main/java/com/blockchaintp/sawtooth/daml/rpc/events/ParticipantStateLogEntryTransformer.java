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

import java.util.ArrayList;
import java.util.List;

import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntry;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntryId;
import com.daml.ledger.participant.state.kvutils.KeyValueConsumption;
import com.daml.ledger.participant.state.v1.Update;

import scala.collection.Iterator;

/**
 * Implementation of LogEntryTransformer based on participant-state v1.
 *
 */
public class ParticipantStateLogEntryTransformer implements LogEntryTransformer {

  @Override
  public final List<Update> logEntryUpdate(final DamlLogEntryId entryId, final DamlLogEntry logEntry) {
    scala.collection.immutable.List<Update> updateList = KeyValueConsumption.logEntryToUpdate(entryId, logEntry);
    List<Update> retList = new ArrayList<>();
    Iterator<Update> iterator = updateList.iterator();
    while (iterator.hasNext()) {
      retList.add(iterator.next());
    }
    return retList;
  }
}
