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
package com.blockchaintp.sawtooth.daml.rpc

import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import com.codahale.metrics.SharedMetricRegistries
import scala.concurrent.duration._
import com.blockchaintp.utils.DirectoryKeyManager

import com.daml.ledger.participant.state.v1.{
  ParticipantId,
  ReadService,
  WriteService,
  UploadPackagesResult
}
import com.digitalasset.ledger.api.domain.LedgerId
import com.digitalasset.ledger.server.apiserver.{
  ApiServer,
  ApiServices,
  LedgerApiServer
}
import com.digitalasset.daml.lf.engine.Engine
import com.digitalasset.platform.index.{
  StandaloneIndexServer,
  StandaloneIndexerServer
}
import com.digitalasset.api.util.TimeProvider
import com.digitalasset.platform.common.logging.NamedLoggerFactory
import com.digitalasset.daml.lf.archive.DarReader
import com.digitalasset.daml_lf_dev.DamlLf.Archive
import com.digitalasset.ledger.api.auth.AuthServiceWildcard
import java.util.concurrent.CompletionStage;

import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Await, Future}
import scala.util.Try
import scala.util.control.NonFatal

object SawtoothDamlRpc extends App {
  val logger = LoggerFactory.getLogger(this.getClass)

  val config = Cli
    .parse(
      args,
      "sawtooth-daml-rpc",
      "A DAML Ledger API server backed by a Hyperledger Sawtooth blockchain network.\n"
    )
    .getOrElse(sys.exit(1))

  // Initialize Akka and log exceptions in flows.
  implicit val system: ActorSystem = ActorSystem("sawtooth-daml-rpc")
  implicit val materializer: ActorMaterializer = ActorMaterializer(
    ActorMaterializerSettings(system)
      .withSupervisionStrategy { e =>
        logger.error(s"Supervision caught exception: $e")
        Supervision.Stop
      }
  )
  implicit val ec: ExecutionContext = system.dispatcher

  logger.error(s"Connecting to " + config.connect)
  val validatorAddress = config.connect
  val swTxnTracer = new SawtoothTransactionsTracer(5051)

  val keyManager = DirectoryKeyManager.create(config.keystore)
  // This should be a more arbitrary identifier, which is set via CLI,
  val writeService = new SawtoothWriteService(
    validatorAddress,
    keyManager,
    null,
    config.participantId
  )
  val readService = new SawtoothReadService(validatorAddress, swTxnTracer, true)

  // Use the default key for this RPC to verify JWTs for now.
  val authService = new DamlAuthServices(keyManager.getPublicKeyInHex())

  config.archiveFiles.foreach { file =>
    for {
      dar <- DarReader { case (_, x) => Try(Archive.parseFrom(x)) }
        .readArchiveFromFile(file)
    } yield writeService.uploadPackages(
      dar.all,
      Some("uploaded on participant startup")
    )
  }

  val indexer = Await.result(
    StandaloneIndexerServer(
      readService,
      config.makePlatformConfig,
      NamedLoggerFactory.forParticipant(config.participantId)
    ),
    30 second
  )
  val indexServer = Await.result(
    StandaloneIndexServer(
      config.makePlatformConfig,
      readService,
      writeService,
      authService,
      NamedLoggerFactory.forParticipant(config.participantId)
    ).start(),
    60 second
  )

  val closed = new AtomicBoolean(false)

  def closeServer(): Unit = {
    if (closed.compareAndSet(false, true)) {
      indexer.close()
      indexServer.close()
      materializer.shutdown()
      val _ = system.terminate()
    }
  }

  try {
    Runtime.getRuntime.addShutdownHook(new Thread(() => closeServer()))
  } catch {
    case NonFatal(t) => {
      logger.error(
        "Shutting down Sandbox application because of initialization error",
        t
      )
      closeServer()
    }
  }
}
