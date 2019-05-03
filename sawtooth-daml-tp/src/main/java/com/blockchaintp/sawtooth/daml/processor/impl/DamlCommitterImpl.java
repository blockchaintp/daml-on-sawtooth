package com.blockchaintp.sawtooth.daml.processor.impl;

import java.util.HashMap;

import com.blockchaintp.sawtooth.daml.processor.DamlCommitter;
import com.daml.ledger.participant.state.kvutils.KeyValueCommitting;
import com.daml.ledger.participant.state.v1.Configuration;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntry;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntryId;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlStateKey;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlStateValue;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlSubmission;
import com.digitalasset.daml.lf.data.Time.Timestamp;
import com.digitalasset.daml.lf.engine.Engine;

import scala.Option;
import scala.Predef;
import scala.Tuple2;
import scala.collection.JavaConverters;
import scala.collection.immutable.Map;

public class DamlCommitterImpl implements DamlCommitter {
  private Engine engine;

  public DamlCommitterImpl(final Engine damlEngine) {
    this.engine = damlEngine;
  }

  @Override
  public final Tuple2<DamlLogEntry, java.util.Map<DamlStateKey, DamlStateValue>> processSubmission(
      final Configuration config, final DamlLogEntryId entryId, final Timestamp recordTime,
      final DamlSubmission submission, final java.util.Map<DamlLogEntryId, DamlLogEntry> inputLogEntries,
      final java.util.Map<DamlStateKey, DamlStateValue> stateMap) {

    HashMap<DamlStateKey, Option<DamlStateValue>> stateMapWithOption = new HashMap<>();
    for (java.util.Map.Entry<DamlStateKey, DamlStateValue> e : stateMap.entrySet()) {
      Option<DamlStateValue> o = Option.apply(e.getValue());
      stateMapWithOption.put(e.getKey(), o);
    }
    Tuple2<DamlLogEntry, Map<DamlStateKey, DamlStateValue>> processedSubmission = KeyValueCommitting.processSubmission(
        this.engine, config, entryId, recordTime, submission, mapToScalaImmutableMap(inputLogEntries),
        mapToScalaImmutableMap(stateMapWithOption));
    DamlLogEntry logEntry = processedSubmission._1;
    java.util.Map<DamlStateKey, DamlStateValue> stateUpdateMap = scalaMapToMap(processedSubmission._2);
    return Tuple2.apply(logEntry, stateUpdateMap);
  }

  @SuppressWarnings("deprecation")
  private <A, B> Map<A, B> mapToScalaImmutableMap(final java.util.Map<A, B> m) {
    return JavaConverters.mapAsScalaMapConverter(m).asScala().toMap(Predef.<Tuple2<A, B>>conforms());
  }

  private <A, B> java.util.Map<A, B> scalaMapToMap(final Map<A, B> m) {
    return JavaConverters.mapAsJavaMap(m);
  }

}
