package com.blockchaintp.sawtooth.daml.rpc.events;

import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntry;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntryId;
import com.daml.ledger.participant.state.kvutils.KeyValueConsumption;
import com.daml.ledger.participant.state.v1.Update;

public class ParticipantStateLogEntryTransformer implements LogEntryTransformer {

  @Override
  public Update logEntryUpdate(DamlLogEntryId entryId, DamlLogEntry logEntry) {
    return KeyValueConsumption.logEntryToUpdate(entryId, logEntry);
  }
}
