package com.blockchaintp.sawtooth.daml.processor;

import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;

import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntry;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntryId;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlStateKey;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlStateValue;

import sawtooth.sdk.processor.exceptions.InternalError;
import sawtooth.sdk.processor.exceptions.InvalidTransactionException;

/**
 * An interface to keep the coupling to the context implementation loose.
 * @author scealiontach
 */
public interface LedgerState {
  /**
   * Fetch a single DamlStateValue from the ledger state.
   * @param key DamlStateKey identifying the operation
   * @return the DamlStateValue for this key or null
   * @throws InvalidTransactionException when there is an error relating to the
   *                                     client input
   * @throws InternalError               when there is an unexpected back end
   *                                     error.
   */
  DamlStateValue getDamlState(DamlStateKey key) throws InternalError, InvalidTransactionException;

  /**
   * Fetch a collection of DamlStateValues from the ledger state.
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
   * Store a collection of log entries into the context.
   * @param entries a collection of tuples of DamlLogEntryId and corresponding
   *                DamlLogEntry
   * @throws InternalError               system error
   * @throws InvalidTransactionException an attempt to read a contract which is
   *                                     not allowed.
   */
  void setDamlLogEntries(Collection<Entry<DamlLogEntryId, DamlLogEntry>> entries)
      throws InternalError, InvalidTransactionException;

  /**
   * @param entryId Id of this log entry
   * @param entry   the log entry to set
   * @throws InvalidTransactionException when there is an error relating to the
   *                                     client input
   * @throws InternalError               when there is an unexpected back end
   *                                     error.
   */
  void setDamlLogEntry(DamlLogEntryId entryId, DamlLogEntry entry) throws InternalError, InvalidTransactionException;


  /**
   * Record an event containing the provided log info.
   * @param entryId the id of this log entry
   * @param entry   the entry itself
   * @throws InternalError when there is an unexpected back end error
   */
  void sendLogEvent(DamlLogEntryId entryId, DamlLogEntry entry) throws InternalError;
}
