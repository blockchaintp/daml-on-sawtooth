/*
 * Copyright © 2023 Paravela Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.blockchaintp.sawtooth.daml.rpc

import java.nio.file.Paths
import java.time.Duration
import akka.stream.Materializer
import com.daml.ledger.api.auth.AuthService
import com.daml.ledger.api.auth.AuthServiceJWT
import com.daml.ledger.api.auth.AuthServiceWildcard
import com.daml.jwt.JwksVerifier
import com.daml.jwt.RSA256Verifier
import com.daml.ledger.participant.state.kvutils.api.KeyValueLedger
import com.daml.ledger.participant.state.kvutils.api.KeyValueParticipantState
import com.daml.ledger.participant.state.kvutils.app.Config
import com.daml.ledger.participant.state.kvutils.app.LedgerFactory
import com.daml.ledger.participant.state.kvutils.app.ParticipantConfig
import com.daml.ledger.participant.state.kvutils.app.Runner
import com.daml.ledger.participant.state.v1.Configuration
import com.daml.ledger.participant.state.v1.TimeModel
import com.daml.lf.engine.Engine
import com.daml.logging.LoggingContext
import com.daml.platform.configuration.LedgerConfiguration
import com.daml.resources.FutureResourceOwner
import com.daml.resources.ProgramResource
import org.slf4j.event.Level
import com.blockchaintp.utils.LogUtils
import com.daml.ledger.resources.ResourceContext
import com.daml.ledger.resources.ResourceOwner
import com.daml.platform.apiserver.ApiServerConfig

import scala.util.Try
import scopt.OptionParser

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

object Main {

  def main(args: Array[String]): Unit = {
    val factory = new SawtoothLedgerFactory()
    val runner  = new Runner("Sawtooth Ledger", factory).owner(args)
    new ProgramResource(runner).run(ResourceContext.apply)
  }

  class SawtoothLedgerFactory() extends LedgerFactory[KeyValueParticipantState, ExtraConfig] {

    final override def readWriteServiceOwner(
        config: Config[ExtraConfig],
        participantConfig: ParticipantConfig,
        engine: Engine
    )(implicit
        materializer: Materializer,
        logCtx: LoggingContext
    ): ResourceOwner[KeyValueParticipantState] = {
      LogUtils.setRootLogLevel(config.extra.logLevel)
      LogUtils.setLogLevel("org.flywaydb.core.internal", Level.INFO.name())
      for {
        readerWriter <- owner(config, participantConfig, engine)
      } yield new KeyValueParticipantState(
        readerWriter,
        readerWriter,
        createMetrics(participantConfig, config)
      )
    }

    def owner(config: Config[ExtraConfig], participantConfig: ParticipantConfig, engine: Engine)(implicit
        materializer: Materializer,
        logCtx: LoggingContext
    ): ResourceOwner[KeyValueLedger] = {
      new FutureResourceOwner(() => {
        Future.successful(
          new SawtoothReaderWriter(
            participantConfig.participantId,
            config.extra.zmqUrl,
            config.extra.keystore,
            config.ledgerId,
            config.extra.maxOpsPerBatch,
            config.extra.maxOutStandingBatches
          )
        )
      })
    }

    override def ledgerConfig(config: Config[ExtraConfig]): LedgerConfiguration =
      LedgerConfiguration(
        initialConfiguration = Configuration(
          // NOTE: Any changes the the config content here require that the
          // generation be increased
          generation = 1L,
          timeModel = TimeModel(
            avgTransactionLatency = Duration.ofSeconds(1L),
            minSkew = Duration.ofSeconds(80L),
            maxSkew = Duration.ofSeconds(80L)
          ).get,
          maxDeduplicationTime = Duration.ofDays(1L)
        ),
        initialConfigurationSubmitDelay = Duration.ofSeconds(5L),
        configurationLoadTimeout = Duration.ofSeconds(30L)
      )

    override def authService(config: Config[ExtraConfig]): AuthService = {
      config.extra.authType match {
        case "none" => AuthServiceWildcard
        case "rsa256" =>
          val verifier = RSA256Verifier
            .fromCrtFile(config.extra.secret)
            .valueOr(err => sys.error(s"Failed to create RSA256 verifier for: $err"))
          AuthServiceJWT(verifier)
        case "jwks" =>
          val verifier = JwksVerifier(config.extra.jwksUrl)
          AuthServiceJWT(verifier)
      }
    }

    override val defaultExtraConfig: ExtraConfig = ExtraConfig.default

    private def validatePath(path: String, message: String): Either[String, Unit] = {
      val valid = Try(Paths.get(path).toFile.canRead).getOrElse(false)
      if (valid) Right(()) else Left(message)
    }

    final override def extraConfigParser(parser: OptionParser[Config[ExtraConfig]]): Unit = {
      parser
        .opt[String]("connect")
        .optional()
        .text("ZMQ Url of the validator to connect to")
        .action { case (v, config) =>
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
        .action { case (v, config) =>
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
        .action { case (v, config) =>
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
        .action { case (v, config) =>
          config.copy(
            extra = config.extra.copy(
              maxOpsPerBatch = v.toInt
            )
          )
        }
      parser
        .opt[String]("auth-jwt-rs256-crt")
        .optional()
        .validate(
          validatePath(_, "The certificate file specified via --auth-jwt-rs256-crt does not exist")
        )
        .text(
          "Enables JWT-based authorization, where the JWT is signed by RSA256 with a public key loaded from the given X509 certificate file (.crt)"
        )
        .action { case (v, config) =>
          config.copy(
            extra = config.extra.copy(
              secret = v,
              authType = "rsa256"
            )
          )
        }
      parser
        .opt[String]("auth-jwt-rs256-jwks")
        .optional()
        .validate(v => Either.cond(v.length > 0, (), "JWK server URL must be a non-empty string"))
        .text(
          "Enables JWT-based authorization, where the JWT is signed by RSA256 with a public key loaded from the given JWKS URL"
        )
        .action { case (v, config) =>
          config.copy(
            extra = config.extra.copy(
              jwksUrl = v,
              authType = "jwks"
            )
          )
        }
      parser
        .opt[String]("max-outstanding-batches")
        .optional()
        .text("maximum number of batches outstanding")
        .action { case (v, config) =>
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
