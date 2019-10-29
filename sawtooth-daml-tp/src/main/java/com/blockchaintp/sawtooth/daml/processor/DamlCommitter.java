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
package com.blockchaintp.sawtooth.daml.processor;

import java.util.Map;

import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntry;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntryId;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlStateKey;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlStateValue;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlSubmission;
import com.daml.ledger.participant.state.v1.Configuration;
import com.digitalasset.daml.lf.data.Time.Timestamp;

import sawtooth.sdk.processor.exceptions.InvalidTransactionException;
import scala.Option;
import scala.Tuple2;

/**
 * An interface providing indirection to the KeyValueCommitting class from
 * participant-state-kvutils. This is valuable for unit testing and potential
 * dependency injection.
 *
 * @author scealiontach
 */
public interface DamlCommitter {

  /**
   * Delegate method to the KeyValueCommitting class, but also providing some
   * useful java to scala and vice versa type tidying.
   *
   * @param defaultConfig the default configuration of the Daml ledger
   * @param entryId       the proposed entry id for the log entry resulting from
   *                      this call
   * @param recordTime    the current maximum record time
   * @param submission    the DamlSubmission to be processed
   * @param participantId The id of the participant who provided the submission
   * @param stateMap      the key/value pairs that will be required to
   *                      successfully process this submission
   * @return A scala tuple of DamlLogEntry which should be created, and a map of
   *         the key/value pairs that should be written to the ledger
   * @throws InvalidTransactionException if any error in KeyValueCommitting
   */
  Tuple2<DamlLogEntry, java.util.Map<DamlStateKey, DamlStateValue>> processSubmission(Configuration defaultConfig,
      DamlLogEntryId entryId, Timestamp recordTime, DamlSubmission submission, String participantId,
      Map<DamlStateKey, Option<DamlStateValue>> stateMap) throws InvalidTransactionException;

}
