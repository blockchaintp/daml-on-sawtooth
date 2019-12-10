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
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import com.blockchaintp.utils.DirectoryKeyManager
import com.codahale.metrics.SharedMetricRegistries
import com.digitalasset.daml.lf.archive.DarReader
import com.digitalasset.daml_lf_dev.DamlLf.Archive
import com.digitalasset.ledger.api.auth.AuthServiceWildcard
import com.digitalasset.platform.common.logging.NamedLoggerFactory
import com.digitalasset.platform.index.{StandaloneIndexServer, StandaloneIndexerServer}
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
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
  //Replace this with a key based JWT service
  val authService = AuthServiceWildcard

  config.archiveFiles.foreach { file =>
    for {
      dar <- DarReader { case (_, x) => Try(Archive.parseFrom(x)) }
        .readArchiveFromFile(file)
    } yield
      writeService.uploadPackages(
        dar.all,
        Some("uploaded on participant startup")
      )
  }

  val indexer = Await.result(
    StandaloneIndexerServer(
      readService,
      config.makePlatformConfig,
      NamedLoggerFactory.forParticipant(config.participantId),
      SharedMetricRegistries.getOrCreate(s"ledger-api-server-${config.participantId}")
    ),
    30 second
  )
  val indexServer = Await.result(
    new StandaloneIndexServer(
      config.makePlatformConfig,
      readService,
      writeService,
      authService,
      NamedLoggerFactory.forParticipant(config.participantId),
      SharedMetricRegistries.getOrCreate(s"indexer-${config.participantId}")
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
    case NonFatal(t) =>
      logger.error(
        "Shutting down Sandbox application because of initialization error",
        t
      )
      closeServer()
  }
}
