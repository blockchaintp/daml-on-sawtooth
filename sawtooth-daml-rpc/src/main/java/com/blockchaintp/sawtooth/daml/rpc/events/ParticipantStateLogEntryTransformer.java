package com.blockchaintp.sawtooth.daml.rpc.events;

import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntry;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntryId;
import com.daml.ledger.participant.state.kvutils.KeyValueConsumption;
import com.daml.ledger.participant.state.v1.Update;

/**
 * Implementation of LogEntryTransformer based on participant-state v1.
 *
 */
public class ParticipantStateLogEntryTransformer implements LogEntryTransformer {

  @Override
  public final Update logEntryUpdate(final DamlLogEntryId entryId, final DamlLogEntry logEntry) {
    return KeyValueConsumption.logEntryToUpdate(entryId, logEntry);
  }
}
