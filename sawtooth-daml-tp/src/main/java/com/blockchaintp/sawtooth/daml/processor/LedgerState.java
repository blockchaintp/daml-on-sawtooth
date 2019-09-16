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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntry;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntryId;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlStateKey;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlStateValue;
import com.blockchaintp.sawtooth.daml.protobuf.SawtoothDamlParty;
import com.daml.ledger.participant.state.backport.TimeModel;
import com.google.protobuf.Timestamp;

import sawtooth.sdk.processor.exceptions.InternalError;
import sawtooth.sdk.processor.exceptions.InvalidTransactionException;

/**
 * An interface to keep the coupling to the context implementation loose.
 *
 * @author scealiontach
 */
public interface LedgerState {
  /**
   * Fetch a single DamlStateValue from the ledger state.
   *
   * @param key DamlStateKey identifying the operation
   * @return the DamlStateValue for this key or null
   * @throws InvalidTransactionException when there is an error relating to the
   *                                     client input
   * @throws InternalError               when there is an unexpected back end
   *                                     error.
   */
  DamlStateValue getDamlState(DamlStateKey key) throws InternalError, InvalidTransactionException;

  /**
   * Fetch the party identified by the string partyId. If it does not exist return
   * null. Empty addresses are consided unset.
   *
   * @param partyId the id of the party
   * @return the SawtoothDamlParty of the partyId, or null if it does not exist
   * @throws InvalidTransactionException when there is an error relating to the
   *                                     client input
   * @throws InternalError               when there is an unexpected back end
   *                                     error.
   */
  SawtoothDamlParty getParty(String partyId) throws InternalError, InvalidTransactionException;

  /**
   * Set or update the data at the party identified by partyId.
   *
   * @param partyEntry the party in question.
   * @return the current SawtoothDamlParty entry
   * @throws InvalidTransactionException when there is an error relating to the
   *                                     client input
   * @throws InternalError               when there is an unexpected back end
   *                                     error.
   */
  SawtoothDamlParty setParty(SawtoothDamlParty partyEntry) throws InternalError, InvalidTransactionException;

  /**
   * Add the party id specified if it does not exist otherwise throw
   * InvalidTransactionException. Empty addresses are considered unset.
   *
   * @param partyEntry the data content of the party
   * @throws InvalidTransactionException when there is an error relating to the
   *                                     client input, or the party already
   *                                     exists.
   * @throws InternalError               when there is an unexpected back end
   *                                     error.
   */
  void addParty(SawtoothDamlParty partyEntry) throws InternalError, InvalidTransactionException;

  /**
   * Fetch a collection of DamlStateValues from the ledger state.
   *
   * @param keys a collection DamlStateKeys identifying the object
   * @return a map of DamlStateKey to DamlStateValue
   * @throws InvalidTransactionException when there is an error relating to the
   *                                     client input
   * @throws InternalError               when there is an unexpected back end
   *                                     error.
   */
  Map<DamlStateKey, DamlStateValue> getDamlStates(Collection<DamlStateKey> keys)
      throws InternalError, InvalidTransactionException;

  /**
   * Fetch a collection of DamlStateValues from the ledger state.
   *
   * @param keys one or more DamlStateKeys identifying the values to be fetches
   * @return a map of DamlStateKeys to DamlStateValues
   * @throws InvalidTransactionException when there is an error relating to the
   *                                     client input
   * @throws InternalError               when there is an unexpected back end
   *                                     error.
   */
  Map<DamlStateKey, DamlStateValue> getDamlStates(DamlStateKey... keys)
      throws InternalError, InvalidTransactionException;

  /**
   * Fetch a collection of log entries from the ledger state.
   *
   * @param keys a collection DamlLogEntryIds identifying the objects
   * @return a map of package DamlLogEntryId to DamlLogEntrys
   * @throws InvalidTransactionException when there is an error relating to the
   *                                     client input
   * @throws InternalError               when there is an unexpected back end
   *                                     error.
   */
  Map<DamlLogEntryId, DamlLogEntry> getDamlLogEntries(Collection<DamlLogEntryId> keys)
      throws InternalError, InvalidTransactionException;

  /**
   * Fetch a collection of log entries from the ledger state.
   *
   * @param keys DamlLogEntryIds identifying the objects
   * @return a map of package DamlLogEntryId to DamlLogEntrys
   * @throws InvalidTransactionException when there is an error relating to the
   *                                     client input
   * @throws InternalError               when there is an unexpected back end
   *                                     error.
   */
  Map<DamlLogEntryId, DamlLogEntry> getDamlLogEntries(DamlLogEntryId... keys)
      throws InternalError, InvalidTransactionException;

  /**
   * Get the log entry for a given log entry id.
   *
   * @param entryId identifies the log entry
   * @return the log entry
   * @throws InternalError               system error
   * @throws InvalidTransactionException an attempt to read a contract which is
   *                                     not allowed.
   */
  DamlLogEntry getDamlLogEntry(DamlLogEntryId entryId) throws InternalError, InvalidTransactionException;

  /**
   * @param key The key identifying this DamlStateValue
   * @param val the DamlStateValue
   * @throws InvalidTransactionException when there is an error relating to the
   *                                     client input
   * @throws InternalError               when there is an unexpected back end
   *                                     error.
   */
  void setDamlState(DamlStateKey key, DamlStateValue val) throws InternalError, InvalidTransactionException;

  /**
   * Store a collection of DamlStateValues at the logical keys provided.
   *
   * @param entries a collection of tuples of DamlStateKey to DamlStateValue
   *                mappings
   * @throws InvalidTransactionException when there is an error relating to the
   *                                     client input
   * @throws InternalError               when there is an unexpected back end
   *                                     error.
   */
  void setDamlStates(Collection<Entry<DamlStateKey, DamlStateValue>> entries)
      throws InternalError, InvalidTransactionException;

  /**
   * @param entryId          Id of this log entry
   * @param entry            the log entry to set
   * @return the new entry list after the addition
   * @throws InvalidTransactionException when there is an error relating to the
   *                                     client input
   * @throws InternalError               when there is an unexpected back end
   *                                     error.
   */
  List<String> addDamlLogEntry(DamlLogEntryId entryId, DamlLogEntry entry)
      throws InternalError, InvalidTransactionException;

  /**
   * Record an event containing the provided log info.
   *
   * @param entryId the id of this log entry
   * @param entry   the entry itself
   * @throws InternalError               when there is an unexpected back end
   *                                     error
   * @throws InvalidTransactionException when the data itself is invalid
   */
  void sendLogEvent(DamlLogEntryId entryId, DamlLogEntry entry)
      throws InternalError, InvalidTransactionException;

  /**
   * Fetch the current global record time.
   *
   * @return a Timestamp
   * @throws InternalError when there is an unexpected back end error.
   */
  Timestamp getRecordTime() throws InternalError;

  /**
   * Update the log event index to equal the provided list.
   *
   * @param addresses the list of addresses which are to be set
   * @throws InternalError               when there is an unexpected back end
   *                                     error
   * @throws InvalidTransactionException when the data itself is invalid
   */
  void updateLogEntryIndex(List<String> addresses) throws InternalError, InvalidTransactionException;

  /**
   * Return the current log event index.
   *
   * @return the current log event index
   * @throws InternalError               when there is an unexpected back end
   *                                     error
   * @throws InvalidTransactionException when the data itself is invalid
   */
  List<String> getLogEntryIndex() throws InternalError, InvalidTransactionException;

  /**
   * Retrieve the current TimeModel or null if it has not been set.
   *
   * @return current TimeModel or null if it has not been set
   * @throws InternalError               when there is an unexpected back end
   *                                     error
   * @throws InvalidTransactionException when the data itself is invalid
   */
  TimeModel getTimeModel() throws InternalError, InvalidTransactionException;

  /**
   * Set the TimeModel to be used after this transaction is complete.
   *
   * @param tm the new time model
   * @throws InternalError               when there is an unexpected back end
   *                                     error
   * @throws InvalidTransactionException when the data itself is invalid
   */
  void setTimeModel(TimeModel tm) throws InternalError, InvalidTransactionException;
}
