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

import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Materializer, Supervision}
import com.codahale.metrics.SharedMetricRegistries

import scala.concurrent.duration._
import com.blockchaintp.utils.DirectoryKeyManager
import com.daml.ledger.participant.state.v1.{ReadService, WriteService}
import com.digitalasset.api.util.TimeProvider
import com.digitalasset.platform.common.logging.NamedLoggerFactory
import com.digitalasset.daml.lf.archive.DarReader
import com.digitalasset.daml_lf_dev.DamlLf.Archive
import com.digitalasset.ledger.api.auth.{AuthServiceWildcard, AuthServiceJWT}
import com.digitalasset.jwt.{JwtVerifier, HMAC256Verifier}
import com.digitalasset.platform.indexer.StandaloneIndexerServer
import com.digitalasset.platform.apiserver.{ApiServerConfig, StandaloneApiServer}
import com.digitalasset.ledger.api.auth.{AuthService, AuthServiceWildcard}
import com.digitalasset.platform.indexer.IndexerConfig
import com.digitalasset.platform.resources.{Resource, ResourceOwner}
import org.slf4j.LoggerFactory

import scala.concurrent.{Await, ExecutionContext}
import scala.util.Try
import scala.util.control.NonFatal

object SawtoothDamlRpc extends App {
  val logger = LoggerFactory.getLogger(this.getClass)

  val config = Cli
    .parse(
      args,
      "sawtooth-daml-rpc",
      "A DAML Ledger API server backed by a Hyperledger Sawtooth blockchain network."
    )
    .getOrElse(sys.exit(1))

  // Initialize Akka and log exceptions in flows.
  implicit val system: ActorSystem = ActorSystem("sawtooth-daml-rpc")
  implicit val materializer: Materializer = Materializer(system)
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
  def hmac256(input:String) : AuthServiceJWT = {
    val secret = input.substring(8,input.length())
    val verifier:JwtVerifier = HMAC256Verifier(secret).getOrElse(throw new RuntimeException("Unable to instantiate JWT Verifier"))
    return new AuthServiceJWT(verifier)
  }

  val authService = config.auth match {
    case "wildcard" => AuthServiceWildcard
    case "sawtooth" => new DamlAuthServices(keyManager.getPublicKeyInHex())
    case  s if s.matches("""hmac256=([a-zA-Z0-9])+""") => hmac256(s)
    case _ => AuthServiceWildcard
  }

  val resource = for {
    _ <- ResourceOwner.forActorSystem(() => system).acquire()
    _ <- ResourceOwner.forMaterializer(() => materializer).acquire()
    // Take ownership of the actor system and materializer so they're cleaned up properly.
    // This is necessary because we can't declare them as implicits within a `for` comprehension.

  _ = config.archiveFiles.foreach { file =>
    for {
      dar <- DarReader { case (_, x) => Try(Archive.parseFrom(x)) }
        .readArchiveFromFile(file)
    } yield writeService.uploadPackages(
      UUID.randomUUID().toString,
      dar.all,
      Some("uploaded on participant startup")
    )
  }

  _ <- startIndexerServer(config, readService)
  _ <- startApiServer(config, readService, writeService, authService)

  } yield ()

  resource.asFuture.failed.foreach { exception =>
    logger.error("Shutting down because of an initialization error.", exception)
    System.exit(1)
  }

  Runtime.getRuntime.addShutdownHook(new Thread(() => Await.result(resource.release(), 10.seconds)))

  private def startIndexerServer (config: SawtoothDamlRpcConfig, readService: ReadService): Resource[Unit] =
    new StandaloneIndexerServer(
      readService,
      IndexerConfig(config.participantId, config.jdbcUrl, config.startupMode),
      NamedLoggerFactory.forParticipant(config.participantId),
      SharedMetricRegistries.getOrCreate(s"indexer-standaloneApiServer-${config.participantId}")
    ).acquire()

  private def startApiServer(config: SawtoothDamlRpcConfig,
                             readService: ReadService,
                             writeService: WriteService,
                             authService: AuthService): Resource[Unit] = new StandaloneApiServer(
    ApiServerConfig(config.participantId,
      config.archiveFiles,
      config.port,
      config.jdbcUrl,
      config.tlsConfig,
      TimeProvider.UTC,
      config.maxInboundMessageSize,
      portFile = None),
    readService,
    writeService,
    authService,
    NamedLoggerFactory.forParticipant(config.participantId),
    SharedMetricRegistries.getOrCreate(s"ledger-api-standaloneApiServer-${config.participantId}")
  ).acquire()
}
