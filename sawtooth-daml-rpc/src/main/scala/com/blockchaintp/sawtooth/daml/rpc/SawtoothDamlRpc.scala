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
import com.blockchaintp.utils.InMemoryKeyManager
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

  //TODO this needs to be replaced with a KeyManager that holds the participant info
  val validatorAddress = "tcp://localhost:4004"
  val keyManager = InMemoryKeyManager.createSECP256k1()
  val readService = new SawtoothReadService(validatorAddress)
  val writeService = new SawtoothWriteService(validatorAddress,keyManager)
 

  //val ledger = new Ledger(timeModel, tsb)
  def archivesFromDar(file: File): List[Archive] = {
    DarReader[Archive](x => Try(Archive.parseFrom(x)))
      .readArchive(new ZipFile(file))
      .fold(t => throw new RuntimeException(s"Failed to parse DAR from $file", t), dar => dar.all)
  }

  // Parse DAR archives given as command-line arguments and upload them
  // to the ledger using a side-channel.
/*  config.archiveFiles.foreach { f =>
    archivesFromDar(f).foreach { archive =>
      logger.info(s"Uploading archive ${archive.getHash}...")
      ledger.uploadArchive(archive)
    }
  }
*/
  readService.getLedgerInitialConditions
    .runWith(Sink.head)
    .foreach { initialConditions =>
      val indexService = ReferenceIndexService(
        participantReadService = if (config.badServer) BadReadService(readService) else readService,
        initialConditions = initialConditions
      )

      val server = Server(
        serverPort = config.port,
        sslContext = config.tlsConfig.flatMap(_.server),
        indexService = indexService,
        writeService = writeService,
      )

      // If port file was provided, write out the allocated server port to it.
      config.portFile.foreach { f =>
        val w = new FileWriter(f)
        w.write(s"${server.port}\n")
        w.close
      }

      // Add a hook to close the server. Invoked when Ctrl-C is pressed.
      Runtime.getRuntime.addShutdownHook(new Thread(() => server.close()))
    }(DirectExecutionContext)
}

// simulate a bad read service by returning only
// empty transactions.
final case class BadReadService(readService: ReadService) extends ReadService {
  override def getLedgerInitialConditions(): Source[LedgerInitialConditions, NotUsed] =
    readService.getLedgerInitialConditions

  override def stateUpdates(beginAfter: Option[Offset]): Source[(Offset, Update), NotUsed] =
    readService.stateUpdates(beginAfter).map {
      case (updateId, update) =>
        val updatePrime = update match {
          case tx: Update.TransactionAccepted =>
            tx.copy(transaction = GenTransaction(Map(), ImmArray.empty, Set.empty))
          case _ => update
        }
        (updateId, updatePrime)
    } 
}