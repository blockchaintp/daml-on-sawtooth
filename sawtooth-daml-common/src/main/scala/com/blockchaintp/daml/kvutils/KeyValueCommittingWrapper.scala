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
package com.blockchaintp.daml.kvutils

import com.daml.ledger.participant.state.kvutils.KeyValueCommitting
import com.daml.ledger.participant.state.kvutils.DamlKvutils._
import com.daml.ledger.participant.state.kvutils.committing._
import com.daml.ledger.participant.state.v1.{Configuration, ParticipantId}
import com.digitalasset.daml.lf.data.Time.Timestamp
import com.digitalasset.daml.lf.engine.Engine
import com.digitalasset.daml.lf.transaction.TransactionOuterClass
import com.digitalasset.daml_lf.DamlLf
import com.digitalasset.platform.common.metrics.VarGauge
import com.google.protobuf.ByteString
import org.slf4j.LoggerFactory
import scala.collection.JavaConverters._
import sawtooth.sdk.processor.exceptions.InvalidTransactionException

object KeyValueCommittingWrapper {
  @throws(classOf[InvalidTransactionException])
  def processSubmission(
      engine: Engine,
      entryId: DamlLogEntryId,
      recordTime: Timestamp,
      defaultConfig: Configuration,
      submission: DamlSubmission,
      participantId: ParticipantId,
      inputState: Map[DamlStateKey, Option[DamlStateValue]],
  ): (DamlLogEntry, Map[DamlStateKey, DamlStateValue]) = {
    try {
      return KeyValueCommitting.processSubmission(engine,entryId,recordTime,defaultConfig,submission,participantId,inputState)
    } catch {
      case scala.util.control.NonFatal(e) =>
        throw new InvalidTransactionException("Error processing submission")
    }
  }
}