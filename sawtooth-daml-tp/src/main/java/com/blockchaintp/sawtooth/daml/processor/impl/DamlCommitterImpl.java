/* Copyright 2019 Blockchain Technology Partners
 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at
     http://www.apache.org/licenses/LICENSE-2.0
 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
------------------------------------------------------------------------------*/
package com.blockchaintp.sawtooth.daml.processor.impl;

import com.blockchaintp.sawtooth.daml.processor.DamlCommitter;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntry;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntryId;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlStateKey;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlStateValue;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlSubmission;
import com.blockchaintp.daml.kvutils.KeyValueCommittingWrapper;
import com.daml.ledger.participant.state.v1.Configuration;
import com.digitalasset.daml.lf.data.Time.Timestamp;
import com.digitalasset.daml.lf.engine.Engine;

import sawtooth.sdk.processor.exceptions.InvalidTransactionException;
import scala.Option;
import scala.Predef;
import scala.Tuple2;
import scala.collection.JavaConverters;
import scala.collection.immutable.Map;

/**
 * The concrete implementation of DamlCommitter.
 *
 * @author scealiontach
 */
public class DamlCommitterImpl implements DamlCommitter {
  private Engine engine;

  /**
   * Build a DamlCommitterImpl. The Engine to be used is passed in to allow reuse
   * since Engine initialization can be costly.
   *
   * @param damlEngine the Engine to be used for this committer
   */
  public DamlCommitterImpl(final Engine damlEngine) {
    this.engine = damlEngine;
  }

  @Override
  public final Tuple2<DamlLogEntry, java.util.Map<DamlStateKey, DamlStateValue>> processSubmission(
      final Configuration defaultConfig, final DamlLogEntryId entryId, final Timestamp recordTime,
      final DamlSubmission submission, final String participantId, final java.util.Map<DamlStateKey,
      Option<DamlStateValue>> stateMap) throws InvalidTransactionException {
    Tuple2<DamlLogEntry, Map<DamlStateKey, DamlStateValue>> processedSubmission = KeyValueCommittingWrapper
        .processSubmission(this.engine, entryId, recordTime, defaultConfig, submission, participantId,
        mapToScalaImmutableMap(stateMap));
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
