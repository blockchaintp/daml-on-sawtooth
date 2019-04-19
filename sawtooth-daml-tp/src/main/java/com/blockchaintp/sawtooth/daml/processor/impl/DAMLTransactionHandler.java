package com.blockchaintp.sawtooth.daml.processor.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.blockchaintp.sawtooth.daml.processor.Namespace;
import com.blockchaintp.sawtooth.daml.processor.LedgerState;
import com.blockchaintp.sawtooth.daml.protobuf.TransactionSubmission;
import com.blockchaintp.sawtooth.daml.state.protobuf.Contract;
import com.blockchaintp.sawtooth.daml.state.protobuf.DAMLPackage;
import com.digitalasset.daml.lf.transaction.TransactionOuterClass.Node;
import com.digitalasset.daml.lf.transaction.TransactionOuterClass.Node.NodeTypeCase;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.ProtocolStringList;

import sawtooth.sdk.processor.State;
import sawtooth.sdk.processor.TransactionHandler;
import sawtooth.sdk.processor.exceptions.InternalError;
import sawtooth.sdk.processor.exceptions.InvalidTransactionException;
import sawtooth.sdk.protobuf.TpProcessRequest;
import sawtooth.sdk.protobuf.TransactionHeader;

public class DAMLTransactionHandler implements TransactionHandler {

	private String namespace;
	private String version;
	private String familyName;

	public DAMLTransactionHandler() {
		this.namespace = Namespace.getNameSpace();
		this.version = Namespace.FAMILY_VERSION_1_0;
		this.familyName = Namespace.FAMILY_NAME;
	}

	@Override
	public void apply(TpProcessRequest tpProcessRequest, State state)
			throws InvalidTransactionException, InternalError {

		basicRequestChecks(tpProcessRequest);

		LedgerState ledgerState = new DAMLLedgerState(state);
		TransactionSubmission submission;
		try {
			submission = TransactionSubmission.parseFrom(tpProcessRequest.getPayload());
		} catch (InvalidProtocolBufferException e) {
			InvalidTransactionException ite = new InvalidTransactionException(String.format(
					"Payload is unparseable, and not a valid TransactionSubmission", e.getMessage().getBytes()));
			ite.initCause(e);
			throw ite;
		}

		// Check for a duplicate command
		boolean duplicate = ledgerState.isDuplicateCommand(submission.getSubmitter(), submission.getApplicationId(),
				submission.getCommandId());
		if (duplicate) {
			throw new InvalidTransactionException(String.format(
					"DuplicateCommandRejection: applicationID=%s, commandID=%s is a duplicate command for %s",
					submission.getApplicationId(), submission.getCommandId(), submission.getSubmitter()));
		}

		// 3. Fetch all of the inputs
		// Add all of the contracts to a list of contracts
		// Add all of the templates/packages to a list of templates/packages
		List<String> inputsList = tpProcessRequest.getHeader().getInputsList();
		Collection<String> input_contractAddresses = new ArrayList<>();
		Map<String,Contract> input_contracts = new HashMap<>();
		Map<String,DAMLPackage> input_packages = new HashMap<>();
		
		Collection<String> input_packageAddresses = new ArrayList<>();
		Collection<String> input_duplicateCommands = new ArrayList<>();

		for (String address : inputsList) {
			if (address.startsWith(Namespace.DUPLICATE_COMMAND_NS)) {
				input_duplicateCommands.add(address);
			} else if (address.startsWith(Namespace.CONTRACT_NS)) {
				input_contractAddresses.add(address);
				input_contracts.put(address, ledgerState.getContract(address));
			} else if (address.startsWith(Namespace.PACKAGE_NS)) {
				input_packageAddresses.add(address);
				input_packages.put(address, ledgerState.getDAMLPackage(address));
			}
		}
		

		// 2. from the submission compute the transaction deltas ( contracts to be
		// removed in txDelta.inputs, contracts to be added in txDelta.outputs )
		// At this point all contract_ids may be assumed to be absolute, in fact they
		// need to be since all inputs and outputs need
		// to be known before the transaction is sent to the validator.

		// 3. Validate submission with DAML Engine

		validateSubmission(submission, state);

		throw new InvalidTransactionException("Implementation not yet finished");

	}

	private void validateSubmission(TransactionSubmission submission, State state) {
		// TODO Auto-generated method stub

	}

	private void basicRequestChecks(TpProcessRequest tpProcessRequest)
			throws InvalidTransactionException, InternalError {
		TransactionHeader header = tpProcessRequest.getHeader();
		if (header == null) {
			throw new InvalidTransactionException("Header expected");
		}

		ByteString payload = tpProcessRequest.getPayload();
		if (payload.size() == 0) {
			throw new InvalidTransactionException("Empty payload");
		}

		if (!header.getFamilyName().equals(this.familyName)) {
			throw new InvalidTransactionException("Family name does not match");
		}

		if (!header.getFamilyVersion().contentEquals(this.version)) {
			throw new InvalidTransactionException("Version does not match");
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
