package com.blockchaintp.sawtooth.daml.processor;

import com.digitalasset.daml.lf.value.Value.ContractId;

import sawtooth.sdk.processor.exceptions.InternalError;
import sawtooth.sdk.processor.exceptions.InvalidTransactionException;

import com.blockchaintp.sawtooth.daml.protobuf.AcceptedTransaction;
import com.blockchaintp.sawtooth.daml.state.protobuf.Contract;
import com.blockchaintp.sawtooth.daml.state.protobuf.DAMLPackage;

public interface LedgerState {

	/**
	 * 
	 * @param submitter
	 * @param applicationID
	 * @param commandID
	 * @return
	 */
	boolean isDuplicateCommand(String submitter, String applicationID, String commandID);
	
	/**
	 * 
	 * @param submitter
	 * @param applicationID
	 * @param commandID
	 * @param status
	 */
	void setDuplicateCommand(String submitter, String applicationID, String commandID, boolean status);
	
	Contract getContract(String contractId) throws InternalError, InvalidTransactionException;
	
	void setContract(Contract contract) throws InternalError, InvalidTransactionException;
	
	boolean isActiveContract(String contractId) throws InternalError, InvalidTransactionException;
	
	void setActiveContract(String contractId, boolean active);
	
	/**
	 * 
	 * @param acceptedTraction // This is form of Ledger syncEvent
	 */
	void addLedgerSyncEvent(AcceptedTransaction acceptedTraction);
	
	DAMLPackage getDAMLPackage(String packageId);
	
	void setDAMLPackage(DAMLPackage damlPackage, String packageId);
}


