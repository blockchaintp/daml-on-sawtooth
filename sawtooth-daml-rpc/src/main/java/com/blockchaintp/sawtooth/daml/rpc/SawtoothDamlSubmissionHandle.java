package com.blockchaintp.sawtooth.daml.rpc;

import com.digitalasset.daml.lf.value.Value.AbsoluteContractId;
import com.digitalasset.daml.lf.value.Value.ContractInst;
import com.digitalasset.daml.lf.value.Value.VersionedValue;
import com.digitalasset.ledger.backend.api.v1.SubmissionHandle;
import com.digitalasset.ledger.backend.api.v1.TransactionSubmission;

import scala.Option;
import scala.concurrent.Future;
import scala.runtime.BoxedUnit;

/**
 * An implementation of a DAML SubmissionHandle for Sawtooth.
 * @author scealiontach
 *
 */
public final class SawtoothDamlSubmissionHandle implements SubmissionHandle {

  @Override
  public Future<BoxedUnit> abort() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Future<Option<ContractInst<VersionedValue<AbsoluteContractId>>>> lookupActiveContract(
      final String requestingParty, final AbsoluteContractId contractId) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Future<BoxedUnit> submit(final TransactionSubmission submission) {
    // TODO Auto-generated method stub
    return null;
  }

}
