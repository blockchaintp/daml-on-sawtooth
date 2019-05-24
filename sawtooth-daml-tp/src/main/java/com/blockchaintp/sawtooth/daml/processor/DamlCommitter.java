package com.blockchaintp.sawtooth.daml.processor;

import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntry;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntryId;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlStateKey;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlStateValue;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlSubmission;
import com.daml.ledger.participant.state.v1.Configuration;
import com.digitalasset.daml.lf.data.Time.Timestamp;

import scala.Tuple2;

/**
 * An interface providing indirection to the KeyValueCommitting class from
 * participant-state-kvutils. This is valuable for unit testing and potential
 * dependency injection.
 * @author scealiontach
 */
public interface DamlCommitter {

  /**
   * Delegate method to the KeyValueCommitting class, but also providing some
   * useful java to scala and vice versa type tidying.
   * @param config          the current configuration of the Daml ledger
   * @param entryId         the proposed entry id for the log entry resulting from
   *                        this call
   * @param recordTime      the current maximum record time
   * @param submission      the DamlSubmission to be processed
   * @param inputLogEntries the log entries which will be required as input to
   *                        process this submission
   * @param stateMap        the key/value pairs that will be required to
   *                        successfully process this submission
   * @return A scala tuple of DamlLogEntry which should be created, and a map of
   *         the key/value pairs that should be written to the ledger
   */
  Tuple2<DamlLogEntry, java.util.Map<DamlStateKey, DamlStateValue>> processSubmission(Configuration config,
      DamlLogEntryId entryId, Timestamp recordTime, DamlSubmission submission,
      java.util.Map<DamlLogEntryId, DamlLogEntry> inputLogEntries,
      java.util.Map<DamlStateKey, DamlStateValue> stateMap);

}
