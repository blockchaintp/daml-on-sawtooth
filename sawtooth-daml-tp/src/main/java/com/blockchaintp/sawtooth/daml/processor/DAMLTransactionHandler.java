package com.blockchaintp.sawtooth.daml.processor;

import java.util.Collection;

import sawtooth.sdk.processor.State;
import sawtooth.sdk.processor.TransactionHandler;
import sawtooth.sdk.processor.exceptions.InternalError;
import sawtooth.sdk.processor.exceptions.InvalidTransactionException;
import sawtooth.sdk.protobuf.TpProcessRequest;

public class DAMLTransactionHandler implements TransactionHandler {

	@Override
	public void apply(TpProcessRequest arg0, State arg1) throws InvalidTransactionException, InternalError {
		// TODO Auto-generated method stub

	}

	@Override
	public Collection<String> getNameSpaces() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getVersion() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String transactionFamilyName() {
		// TODO Auto-generated method stub
		return null;
	}

}
