package com.blockchaintp.noop;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Collection;

import sawtooth.sdk.processor.State;
import sawtooth.sdk.processor.TransactionHandler;
import sawtooth.sdk.processor.Utils;
import sawtooth.sdk.processor.exceptions.InternalError;
import sawtooth.sdk.processor.exceptions.InvalidTransactionException;
import sawtooth.sdk.protobuf.TpProcessRequest;

public class NoOpTransactionHandler implements TransactionHandler {

	public final static int ALL_OK = 1;
	public final static int ALL_INVALID_TRANSACTION = 2;
	public final static int ALL_INTERNAL_ERROR = 3;
	private String familyName;
	private String version;
	private String namespace;
	private int strategy;
	
	
	public NoOpTransactionHandler(final String namespace, final String version, final int strategy) {
		this.familyName = namespace;
		this.version = version;
		this.strategy = strategy;
		
		try {
			this.namespace = Utils.hash512(this.familyName.getBytes("UTF-8")).substring(0,6);
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException("UTF-8 encoding not supported on this platform!!",e);
		}
	}
	
	@Override
	public void apply(TpProcessRequest transactionRequest, State state) throws InvalidTransactionException, InternalError {
		switch ( this.strategy ) {
		case ALL_OK:
			return;
		case ALL_INVALID_TRANSACTION:
			throw new InvalidTransactionException("Throwing InvalidTransaction as configured");
		case ALL_INTERNAL_ERROR:
			throw new InternalError("Throwing InternalError as configured");
		}
	}

	@Override
	public Collection<String> getNameSpaces() {
		return Arrays.asList(new String[] { this.namespace });
	}

	@Override
	public String getVersion() {
		return this.version;
	}

	@Override
	public String transactionFamilyName() {
		return this.familyName;
	}

}
