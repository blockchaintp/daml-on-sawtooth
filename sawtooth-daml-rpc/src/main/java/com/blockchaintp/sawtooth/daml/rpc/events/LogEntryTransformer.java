package com.blockchaintp.sawtooth.daml.rpc.events;

import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntry;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntryId;
import com.daml.ledger.participant.state.v1.Update;

/**
 * An interface to introduce a layer of indirection between the processing and
 * the implementation.
 * @author scealiontach
 */
public interface LogEntryTransformer {

  /**
   * Transform a log entry and its id into a single Update.
   * @param entryId  the id of the log entry
   * @param logEntry the entry itself
   * @return an Update suitable for publishing to Daml
   */
  Update logEntryUpdate(DamlLogEntryId entryId, DamlLogEntry logEntry);

}
