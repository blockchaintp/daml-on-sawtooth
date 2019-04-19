package com.blockchaintp.sawtooth.daml.processor.impl;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.blockchaintp.sawtooth.daml.processor.LedgerState;
import com.blockchaintp.sawtooth.daml.processor.Namespace;
import com.blockchaintp.sawtooth.daml.protobuf.AcceptedTransaction;
import com.blockchaintp.sawtooth.daml.state.protobuf.Contract;
import com.blockchaintp.sawtooth.daml.state.protobuf.DAMLPackage;
import com.digitalasset.daml.lf.value.Value.ContractId;

import sawtooth.sdk.processor.State;
import sawtooth.sdk.processor.exceptions.InternalError;
import sawtooth.sdk.processor.exceptions.InvalidTransactionException;

import sawtooth.sdk.processor.Utils;

public class DAMLLedgerState implements LedgerState {

	private State state;

	public DAMLLedgerState(State state) {
		this.state = state;
	}

	@Override
	public boolean isDuplicateCommand(String submitter, String applicationId, String commandId) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void setDuplicateCommand(String submitter, String applicationID, String commandID, boolean status) {
		// TODO Auto-generated method stub

	}

	@Override
	public Contract getContract(String contractId) throws InternalError, InvalidTransactionException {
		String address = Namespace.makeContractAddress(contractId);
		Map<String, ByteString> vals = state.getState(Arrays.asList(address));
		ByteString c = vals.get(address);
		try {
			Contract retContract = Contract.parseFrom(c);
			return retContract;
		} catch (InvalidProtocolBufferException e) {
			throw new InternalError(String.format("Contract data is corrupt for contractId=% at address=%s, msg=%s",
					contractId, address, e.getMessage()));
		}
	}

	@Override
	public void setContract(Contract contract) throws InternalError, InvalidTransactionException {
		Map<String, ByteString> contracts = new HashMap<>();
		contracts.put(Namespace.makeContractAddress(contract.getContractId()), contract.toByteString());
		state.setState(contracts.entrySet());
	}

	@Override
	public boolean isActiveContract(String contractId) throws InternalError, InvalidTransactionException {
		Contract c=getContract(contractId);
		if ( null == c ) {
			return false;
		} else {
			return !c.getArchived();
		}
	}

	@Override
	public void setActiveContract(String contractId, boolean active) {
		// TODO Auto-generated method stub

	}

	@Override
	public void addLedgerSyncEvent(AcceptedTransaction acceptedTraction) {
		// TODO Auto-generated method stub

	}

	@Override
	public DAMLPackage getDAMLPackage(String packageId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setDAMLPackage(DAMLPackage damlPackage, String packageId) {
		// TODO Auto-generated method stub

	}

}
