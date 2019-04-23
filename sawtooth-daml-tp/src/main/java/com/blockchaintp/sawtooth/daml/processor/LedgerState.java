package com.blockchaintp.sawtooth.daml.processor;

import com.blockchaintp.sawtooth.daml.protobuf.AcceptedTransaction;
import com.blockchaintp.sawtooth.daml.state.protobuf.Contract;
import com.blockchaintp.sawtooth.daml.state.protobuf.DAMLPackage;

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
   */
  boolean isDuplicateCommand(String submitter, String applicationID, String commandID);

  /**
   * @param submitter     party who submitted the command
   * @param applicationID application the command was issued fo
   * @param commandID     the id of the command issued
   * @param status        true to set this combination as a command that has been
   *                      issued, false to clear it
   */
  void setDuplicateCommand(String submitter, String applicationID, String commandID, boolean status);

  /**
   * Get the contract for a given contractId.
   * @param contractId the string representation of the contract (not a byte
   *                   string)
   * @return the corresponding contract or null
   * @throws InternalError               system error
   * @throws InvalidTransactionException an attempt to read a contract which is
   *                                     not allowed.
   */
  Contract getContract(String contractId) throws InternalError, InvalidTransactionException;

  /**
   * Store a contract in the context.
   * @param contract the contract
   * @throws InternalError               system error
   * @throws InvalidTransactionException an attempt to read a contract which is
   *                                     not allowed.
   */
  void setContract(Contract contract) throws InternalError, InvalidTransactionException;

  /**
   * Check if a contract is marked as active given its logical contractId.
   * @param contractId the string representation of the contract (not a byte
   *                   string)
   * @return true if the contract is active
   * @throws InternalError               system error
   * @throws InvalidTransactionException an attempt to read a contract which is
   *                                     not allowed.
   */
  boolean isActiveContract(String contractId) throws InternalError, InvalidTransactionException;

  /**
   * Mark the contract referred to by the contractId active based on the argument.
   * @param contractId the string representation of the contract (not a byte
   *                   string)
   * @param active     true for active, otherwise false
   */
  void setActiveContract(String contractId, boolean active);

  /**
   * @param acceptedTraction // This is form of Ledger syncEvent
   */
  void addLedgerSyncEvent(AcceptedTransaction acceptedTraction);

  /**
   * Fetch the DAML Package referred to by the packageId.
   * @param packageId the logical packageId of the package
   * @return the package
   */
  DAMLPackage getDAMLPackage(String packageId);

  /**
   * Store the given package at the logical address packageId.
   * @param damlPackage the DAMLPackage
   * @param packageId the logical identifier for the package
   */
  void setDAMLPackage(DAMLPackage damlPackage, String packageId);
}
