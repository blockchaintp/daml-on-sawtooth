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
   * @param submitter     party who submitted the command
   * @param applicationID application the command was issued fo
   * @param commandID     the id of the command issued
   * @return true if this command has been recorded, otherwise false
   * @throws InvalidTransactionException
   * @throws InternalError
   */
  DamlCommandDedupValue getDamlCommandDedup(DamlCommandDedupKey commandKey)
      throws InternalError, InvalidTransactionException;

  Map<DamlStateKey, DamlStateValue> getDamlCommandDedups(Collection<DamlStateKey> keys)
      throws InternalError, InvalidTransactionException;

  Map<DamlCommandDedupKey, DamlCommandDedupValue> getDamlCommandDedups(DamlCommandDedupKey... keys)
      throws InternalError, InvalidTransactionException;

  Map<DamlStateKey, DamlStateValue> getDamlCommandDedups(DamlStateKey... keys)
      throws InternalError, InvalidTransactionException;

  /**
   * Get the contract for a given contractId.
   * @param contractId the string representation of the contract (not a byte
   *                   string)
   * @return the corresponding contract or null
   * @throws InternalError               system error
   * @throws InvalidTransactionException an attempt to read a contract which is
   *                                     not allowed.
   */
  DamlContractState getDamlContract(DamlContractId contractId) throws InternalError, InvalidTransactionException;

  Map<DamlStateKey, DamlStateValue> getDamlContracts(Collection<DamlStateKey> keys)
      throws InternalError, InvalidTransactionException;

  Map<DamlContractId, DamlContractState> getDamlContracts(DamlContractId... keys)
      throws InternalError, InvalidTransactionException;

  Map<DamlStateKey, DamlStateValue> getDamlContracts(DamlStateKey... keys)
      throws InternalError, InvalidTransactionException;

  Map<DamlLogEntryId, DamlLogEntry> getDamlLogEntries(Collection<DamlLogEntryId> keys)
      throws InternalError, InvalidTransactionException;

  Map<DamlLogEntryId, DamlLogEntry> getDamlLogEntries(DamlLogEntryId... keys)
      throws InternalError, InvalidTransactionException;

  DamlLogEntry getDamlLogEntry(DamlLogEntryId entryId) throws InternalError, InvalidTransactionException;

  /**
   * Fetch the DAML Package referred to by the packageId.
   * @param packageId the logical packageId of the package
   * @return the package
   * @throws InvalidTransactionException
   * @throws InternalError
   */
  Archive getDamlPackage(String packageId) throws InternalError, InvalidTransactionException;

  Map<DamlStateKey, DamlStateValue> getDamlPackages(Collection<DamlStateKey> keys)
      throws InternalError, InvalidTransactionException;

  Map<DamlStateKey, DamlStateValue> getDamlPackages(DamlStateKey... keys)
      throws InternalError, InvalidTransactionException;

  Map<String, Archive> getDamlPackages(String... keys) throws InternalError, InvalidTransactionException;

  /**
   * @param submitter     party who submitted the command
   * @param applicationID application the command was issued fo
   * @param commandID     the id of the command issued
   * @param status        true to set this combination as a command that has been
   *                      issued, false to clear it
   * @throws InvalidTransactionException
   * @throws InternalError
   */
  void setDamlCommandDedup(DamlCommandDedupKey key, DamlCommandDedupValue val)
      throws InternalError, InvalidTransactionException;

  /**
   * Store a contract in the context.
   * @param contract the contract
   * @throws InternalError               system error
   * @throws InvalidTransactionException an attempt to read a contract which is
   *                                     not allowed.
   */
  void setDamlContract(DamlContractId key, DamlContractState val) throws InternalError, InvalidTransactionException;

  void setDamlLogEntries(Collection<Entry<DamlLogEntryId, DamlLogEntry>> entries)
      throws InternalError, InvalidTransactionException;

  /**
   * @param acceptedTraction // This is form of Ledger syncEvent
   * @throws InvalidTransactionException
   * @throws InternalError
   */
  void setDamlLogEntry(DamlLogEntryId entryId, DamlLogEntry entry) throws InternalError, InvalidTransactionException;

  /**
   * Store the given package at the logical address packageId.
   * @param damlPackage the DAMLPackage
   * @param packageId   the logical identifier for the package
   */
  void setDamlPackage(String key, Archive val) throws InternalError, InvalidTransactionException;

  void setDamlPackages(Collection<Entry<String, Archive>> entries) throws InternalError, InvalidTransactionException;

}
