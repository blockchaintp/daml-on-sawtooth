package com.blockchaintp.sawtooth.daml.rpc;

import com.digitalasset.ledger.backend.api.v1.ActiveContract;
import com.digitalasset.ledger.backend.api.v1.LedgerBackend;
import com.digitalasset.ledger.backend.api.v1.LedgerSyncEvent;
import com.digitalasset.ledger.backend.api.v1.SubmissionHandle;

import akka.NotUsed;
import akka.stream.scaladsl.Source;
import scala.Option;
import scala.Tuple2;
import scala.concurrent.Future;

public class SawtoothDamlLedgerBackend implements LedgerBackend {

	@Override
	public Future<Tuple2<String, Source<ActiveContract, NotUsed>>> activeContractSetSnapshot() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Future<SubmissionHandle> beginSubmission() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Future<String> getCurrentLedgerEnd() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String ledgerId() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Source<LedgerSyncEvent, NotUsed> ledgerSyncEvents(Option<String> offset) {
		// TODO Auto-generated method stub
		return null;
	}

}
