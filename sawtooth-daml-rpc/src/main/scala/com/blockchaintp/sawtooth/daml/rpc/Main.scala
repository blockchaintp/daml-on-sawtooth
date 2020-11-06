// Copyright (c) 2020 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.blockchaintp.sawtooth.daml.rpc

import java.time.Duration

import akka.stream.Materializer
import com.daml.ledger.participant.state.kvutils.api.{KeyValueLedger, KeyValueParticipantState}
import com.daml.ledger.participant.state.kvutils.app.{Config, LedgerFactory, ParticipantConfig, Runner}
import com.daml.ledger.participant.state.v1.{Configuration, TimeModel}
import com.daml.lf.engine.Engine
import com.daml.logging.LoggingContext
import com.daml.platform.configuration.LedgerConfiguration
import com.daml.resources.{ProgramResource, ResourceOwner}
import com.blockchaintp.utils.LogUtils
import org.slf4j.event.Level
import scopt.OptionParser

object Main {
  def main(args: Array[String]): Unit = {
    val factory = new SawtoothLedgerFactory()
    val runner = new Runner("Sawtooth Ledger", factory).owner(args)
    new ProgramResource(runner).run()
  }

  class SawtoothLedgerFactory()
      extends LedgerFactory[KeyValueParticipantState, ExtraConfig] {

    override final def readWriteServiceOwner(
        config: Config[ExtraConfig],
        participantConfig: ParticipantConfig,
        engine: Engine,
    )(
        implicit materializer: Materializer,
        logCtx: LoggingContext,
    ): ResourceOwner[KeyValueParticipantState] = {
      LogUtils.setRootLogLevel(config.extra.logLevel)
      LogUtils.setLogLevel("org.flywaydb.core.internal", Level.INFO.name())

      for {
        readerWriter <- owner(config, participantConfig, engine)
      } yield
        new KeyValueParticipantState(
          readerWriter,
          readerWriter,
          createMetrics(participantConfig, config),
        )
    }

    def owner(config: Config[ExtraConfig], participantConfig: ParticipantConfig, engine: Engine)(
        implicit materializer: Materializer,
        logCtx: LoggingContext,
    ): ResourceOwner[KeyValueLedger] = {
      new SawtoothReaderWriter.Owner(participantConfig.participantId,
        config.extra.zmqUrl,
        config.extra.keystore,
        config.extra.maxOpsPerBatch,
        config.extra.maxOutStandingBatches,
        config.ledgerId)
    }

    override def ledgerConfig(config: Config[ExtraConfig]): LedgerConfiguration =
      LedgerConfiguration(
        initialConfiguration = Configuration(
          generation = 1,
          timeModel = TimeModel(
            avgTransactionLatency = Duration.ofSeconds(1L),
            minSkew = Duration.ofSeconds(40L),
            maxSkew = Duration.ofSeconds(80L),
          ).get,
          maxDeduplicationTime = Duration.ofDays(1),
        ),
        initialConfigurationSubmitDelay = Duration.ofSeconds(5),
        configurationLoadTimeout = Duration.ofSeconds(10),
      )

    override val defaultExtraConfig: ExtraConfig = ExtraConfig.default

    override final def extraConfigParser(parser: OptionParser[Config[ExtraConfig]]): Unit = {
      parser
        .opt[String]("connect")
        .optional()
        .text("ZMQ Url of the validator to connect to")
        .action {
          case (v, config) =>
            config.copy(
              extra = config.extra.copy(
                zmqUrl = v
              )
            )
        }
      parser
        .opt[String]("keystore")
        .optional()
        .text("Directory of the keystore")
        .action {
          case (v, config) =>
            config.copy(
              extra = config.extra.copy(
                keystore = v
              )
            )
        }
      parser
        .opt[String]("log-level")
        .optional()
        .text("set log level (warn,info,debug,trace)")
        .action {
          case (v, config) =>
            config.copy(
              extra = config.extra.copy(
                logLevel = v
              )
            )
        }
      parser
        .opt[String]("max-ops-per-batch")
        .optional()
        .text("maximum number of operations per batch")
        .action {
          case (v, config) =>
            config.copy(
              extra = config.extra.copy(
                maxOpsPerBatch = v.toInt
              )
            )
        }
      parser
        .opt[String]("max-outstanding-batches")
        .optional()
        .text("maximum number of batches outstanding")
        .action {
          case (v, config) =>
            config.copy(
              extra = config.extra.copy(
                maxOutStandingBatches = v.toInt
              )
            )
        }
      ()
    }
  }
}
