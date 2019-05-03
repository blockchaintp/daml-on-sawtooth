package com.blockchaintp.sawtooth.daml.processor;

import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;

import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlCommandDedupKey;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlCommandDedupValue;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlContractId;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlContractState;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntry;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntryId;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlStateKey;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlStateValue;
import com.digitalasset.daml_lf.DamlLf.Archive;

import sawtooth.sdk.processor.exceptions.InternalError;
import sawtooth.sdk.processor.exceptions.InvalidTransactionException;

/**
 * An interface to keep the coupling to the context implementation loose.
 * @author scealiontach
 */
public interface LedgerState {
  /**
   * Fetch a single DamlCommandDedupValue from the ledger state.
   * @param commandKey DamlCommandDedupKey identifying the operation
   * @return true if this command has been recorded, otherwise false
   * @throws InvalidTransactionException when there is an error relating to the
   *                                     client input
   * @throws InternalError               when there is an unexpected back end
   *                                     error.
   */
  DamlCommandDedupValue getDamlCommandDedup(DamlCommandDedupKey commandKey)
      throws InternalError, InvalidTransactionException;

  /**
   * Fetch a collection of DamlCommandDedupValues encapsulated as DamlStateValues
   * from the ledger state.
   * @param keys a collection DamlStateKeys identifying the object
   * @return a map of DamlStateKey to DamlStateValue
   * @throws InvalidTransactionException when there is an error relating to the
   *                                     client input
   * @throws InternalError               when there is an unexpected back end
   *                                     error.
   */
  Map<DamlStateKey, DamlStateValue> getDamlCommandDedups(Collection<DamlStateKey> keys)
      throws InternalError, InvalidTransactionException;

  /**
   * Fetch a collection of DamlCommandDedupValues from the ledger state.
   * @param keys DamlCommandDedupKeys identifying the object
   * @return a map of DamlCommandDedupKeys to DamlDedupValues
   * @throws InvalidTransactionException when there is an error relating to the
   *                                     client input
   * @throws InternalError               when there is an unexpected back end
   *                                     error.
   */
  Map<DamlCommandDedupKey, DamlCommandDedupValue> getDamlCommandDedups(DamlCommandDedupKey... keys)
      throws InternalError, InvalidTransactionException;

  /**
   * Fetch a collection of DamlCommandDedupValues encapsulated as DamlStateValues
   * from the ledger state.
   * @param keys DamlStateKeys identifying the object
   * @return a map of DamlStateKey to DamlStateValue
   * @throws InvalidTransactionException when there is an error relating to the
   *                                     client input
   * @throws InternalError               when there is an unexpected back end
   *                                     error.
   */
  Map<DamlStateKey, DamlStateValue> getDamlCommandDedups(DamlStateKey... keys)
      throws InternalError, InvalidTransactionException;

  /**
   * Get the contract for a given contractId.
   * @param contractId identifying the contract
   * @return the corresponding contract
   * @throws InternalError               system error
   * @throws InvalidTransactionException an attempt to read a contract which is
   *                                     not allowed.
   */
  DamlContractState getDamlContract(DamlContractId contractId) throws InternalError, InvalidTransactionException;

  /**
   * Fetch a collection of DamlContractStates encapsulated as DamlStateValues from
   * the ledger state.
   * @param keys a collection DamlStateKeys identifying the object
   * @return a map of DamlStateKey to DamlStateValue
   * @throws InvalidTransactionException when there is an error relating to the
   *                                     client input
   * @throws InternalError               when there is an unexpected back end
   *                                     error.
   */
  Map<DamlStateKey, DamlStateValue> getDamlContracts(Collection<DamlStateKey> keys)
      throws InternalError, InvalidTransactionException;

  /**
   * Fetch a collection of DamlContractStatess from the ledger state.
   * @param keys DamlContractIds identifying the object
   * @return a map of DamlContractIds to DamlContractState
   * @throws InvalidTransactionException when there is an error relating to the
   *                                     client input
   * @throws InternalError               when there is an unexpected back end
   *                                     error.
   */
  Map<DamlContractId, DamlContractState> getDamlContracts(DamlContractId... keys)
      throws InternalError, InvalidTransactionException;

  /**
   * Fetch a collection of DamlContractStates encapsulated as DamlStateValues from
   * the ledger state.
   * @param keys DamlStateKeys identifying the object
   * @return a map of DamlStateKey to DamlStateValue
   * @throws InvalidTransactionException when there is an error relating to the
   *                                     client input
   * @throws InternalError               when there is an unexpected back end
   *                                     error.
   */
  Map<DamlStateKey, DamlStateValue> getDamlContracts(DamlStateKey... keys)
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
   * Fetch the DAML Package referred to by the packageId.
   * @param packageId the logical packageId of the package
   * @return the package
   * @throws InvalidTransactionException when there is an error relating to the
   *                                     client input
   * @throws InternalError               when there is an unexpected back end
   *                                     error.
   */
  Archive getDamlPackage(String packageId) throws InternalError, InvalidTransactionException;

  /**
   * Fetch a collection of Archives encapsulated as DamlStateValues from the
   * ledger state.
   * @param keys a collection DamlStateKeys identifying the object
   * @return a map of DamlStateKey to DamlStateValue
   * @throws InvalidTransactionException when there is an error relating to the
   *                                     client input
   * @throws InternalError               when there is an unexpected back end
   *                                     error.
   */
  Map<DamlStateKey, DamlStateValue> getDamlPackages(Collection<DamlStateKey> keys)
      throws InternalError, InvalidTransactionException;

  /**
   * Fetch a collection of Archives encapsulated as DamlStateValues from the
   * ledger state.
   * @param keys DamlStateKeys identifying the object
   * @return a map of DamlStateKey to DamlStateValue
   * @throws InvalidTransactionException when there is an error relating to the
   *                                     client input
   * @throws InternalError               when there is an unexpected back end
   *                                     error.
   */
  Map<DamlStateKey, DamlStateValue> getDamlPackages(DamlStateKey... keys)
      throws InternalError, InvalidTransactionException;

  /**
   * Fetch a collection of Archives from the ledger state.
   * @param keys package ids identifying the object
   * @return a map of package ids to Archives
   * @throws InvalidTransactionException when there is an error relating to the
   *                                     client input
   * @throws InternalError               when there is an unexpected back end
   *                                     error.
   */
  Map<String, Archive> getDamlPackages(String... keys) throws InternalError, InvalidTransactionException;

  /**
   * @param key The key identifying this command
   * @param val data relating to this command
   * @throws InvalidTransactionException when there is an error relating to the
   *                                     client input
   * @throws InternalError               when there is an unexpected back end
   *                                     error.
   */
  void setDamlCommandDedup(DamlCommandDedupKey key, DamlCommandDedupValue val)
      throws InternalError, InvalidTransactionException;

  /**
   * Store a contract in the context.
   * @param key the id of this contract
   * @param val the state of this contract
   * @throws InternalError               system error
   * @throws InvalidTransactionException an attempt to read a contract which is
   *                                     not allowed.
   */
  void setDamlContract(DamlContractId key, DamlContractState val) throws InternalError, InvalidTransactionException;

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
   * Store the given package at the logical address packageId.
   * @param key the Daml PackageId string
   * @param val the Daml archive package itself
   * @throws InvalidTransactionException when there is an error relating to the
   *                                     client input
   * @throws InternalError               when there is an unexpected back end
   *                                     error.
   */
  void setDamlPackage(String key, Archive val) throws InternalError, InvalidTransactionException;

  /**
   * Store a collection of archives at the logical address provided.
   * @param entries a collection of tuples of package id to Archive mappings
   * @throws InvalidTransactionException when there is an error relating to the
   *                                     client input
   * @throws InternalError               when there is an unexpected back end
   *                                     error.
   */
  void setDamlPackages(Collection<Entry<String, Archive>> entries) throws InternalError, InvalidTransactionException;

}
