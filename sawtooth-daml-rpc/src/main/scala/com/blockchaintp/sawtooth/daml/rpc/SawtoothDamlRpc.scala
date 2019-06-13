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

import java.io.{File, FileWriter}
import java.util.zip.ZipFile

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import com.daml.ledger.api.server.damlonx.Server
import com.daml.ledger.participant.state.index.v1.impl.reference.ReferenceIndexService
import com.daml.ledger.participant.state.v1.{LedgerInitialConditions, Offset, ReadService, Update}
import com.digitalasset.daml.lf.archive.DarReader
import com.digitalasset.daml.lf.data.ImmArray
import com.digitalasset.daml.lf.transaction.GenTransaction
import com.digitalasset.daml_lf.DamlLf.Archive
import com.digitalasset.platform.common.util.DirectExecutionContext
import com.blockchaintp.utils.DirectoryKeyManager
import org.slf4j.LoggerFactory
 
import scala.util.Try

object SawtoothDamlRpc extends App {
 val logger = LoggerFactory.getLogger(this.getClass)

  val config = Cli.parse(args).getOrElse(sys.exit(1))

  // Initialize Akka and log exceptions in flows.
  implicit val system: ActorSystem = ActorSystem("ReferenceServer")
  implicit val materializer: ActorMaterializer = ActorMaterializer(
    ActorMaterializerSettings(system)
      .withSupervisionStrategy { e =>
        logger.error(s"Supervision caught exception: $e")
        Supervision.Stop
      })

  //TODO: constants here should be replaced with CLI args
  logger.error(s"Connecting to "+config.connect)
  val validatorAddress = config.connect
  val swTxnTracer = new SawtoothTransactionsTracer(5051)
  
  val keyManager = DirectoryKeyManager.create(config.keystore)
  val readService = new SawtoothReadService("this-ledger-id",validatorAddress,swTxnTracer)
  val writeService = new SawtoothWriteService(validatorAddress,keyManager, swTxnTracer)
 
  //val ledger = new Ledger(timeModel, tsb)
  def archivesFromDar(file: File): List[Archive] = {
    DarReader[Archive](x => Try(Archive.parseFrom(x)))
      .readArchive(new ZipFile(file))
      .fold(t => throw new RuntimeException(s"Failed to parse DAR from $file", t), dar => dar.all)
  }

  // Parse DAR archives given as command-line arguments and upload them
  // to the ledger using a side-channel.
  config.archiveFiles.foreach { f =>
    archivesFromDar(f).foreach { archive =>
      logger.info(s"Uploading archive ${archive.getHash}...")
      writeService.uploadArchive(archive)
    }
  }

  readService.getLedgerInitialConditions
    .runWith(Sink.head)
    .foreach { initialConditions =>
      val indexService = ReferenceIndexService(
        participantReadService = readService,
        initialConditions = initialConditions
      )

      val server = Server(
        serverPort = config.port,
//        sslContext = config.tlsConfig.flatMap(_.server),
        sslContext = None,
        indexService = indexService,
        writeService = writeService,
      )

      // Add a hook to close the server. Invoked when Ctrl-C is pressed.
      Runtime.getRuntime.addShutdownHook(new Thread(() => server.close()))
    }(DirectExecutionContext)
}
