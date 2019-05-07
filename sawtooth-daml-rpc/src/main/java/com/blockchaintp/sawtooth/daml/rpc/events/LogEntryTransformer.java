package com.blockchaintp.sawtooth.daml.rpc.events;

import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntry;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntryId;
import com.daml.ledger.participant.state.v1.Update;

public interface LogEntryTransformer {

  Update logEntryUpdate(DamlLogEntryId entryId, DamlLogEntry logEntry);

}
