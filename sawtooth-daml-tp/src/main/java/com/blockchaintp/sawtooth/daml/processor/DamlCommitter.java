package com.blockchaintp.sawtooth.daml.processor;

import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntry;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntryId;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlStateKey;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlStateValue;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlSubmission;
import com.daml.ledger.participant.state.v1.Configuration;
import com.digitalasset.daml.lf.data.Time.Timestamp;

import scala.Tuple2;

public interface DamlCommitter {

  Tuple2<DamlLogEntry, java.util.Map<DamlStateKey, DamlStateValue>> processSubmission(Configuration config,
      DamlLogEntryId entryId, Timestamp recordTime, DamlSubmission submission,
      java.util.Map<DamlLogEntryId, DamlLogEntry> inputLogEntries,
      java.util.Map<DamlStateKey, DamlStateValue> stateMapWithOption);

}
