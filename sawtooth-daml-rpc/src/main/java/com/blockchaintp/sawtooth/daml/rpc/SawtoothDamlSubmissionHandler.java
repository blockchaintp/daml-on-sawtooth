package com.blockchaintp.sawtooth.daml.rpc;

import com.digitalasset.daml.lf.value.Value.AbsoluteContractId;
import com.digitalasset.daml.lf.value.Value.ContractInst;
import com.digitalasset.daml.lf.value.Value.VersionedValue;
import com.digitalasset.ledger.backend.api.v1.SubmissionHandle;
import com.digitalasset.ledger.backend.api.v1.TransactionSubmission;

import scala.Option;
import scala.concurrent.Future;
import scala.runtime.BoxedUnit;

public class SawtoothDamlSubmissionHandler implements SubmissionHandle {

	@Override
	public Future<BoxedUnit> abort() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Future<Option<ContractInst<VersionedValue<AbsoluteContractId>>>> lookupActiveContract(String requestingParty,
			AbsoluteContractId contractId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Future<BoxedUnit> submit(TransactionSubmission submission) {
		// TODO Auto-generated method stub
		return null;
	}

}
