// Copyright (c) 2019 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.digitalasset.ledger.example

import java.time.Instant
import java.util.concurrent.{BlockingQueue, LinkedBlockingQueue, TimeUnit}

import akka.stream.ActorMaterializer
import com.digitalasset.api.util.TimeProvider
import com.digitalasset.daml.lf.data.Ref
import com.digitalasset.daml.lf.value.Value
import com.digitalasset.daml.lf.value.Value.{AbsoluteContractId, VersionedValue}
import com.digitalasset.ledger.backend.api.v1.LedgerSyncEvent.{
  AcceptedTransaction,
  Heartbeat,
  RejectedCommand
}
import com.digitalasset.ledger.backend.api.v1.RejectionReason.{
  Disputed,
  DuplicateCommandId,
  Inconsistent
}
import com.digitalasset.ledger.backend.api.v1._
import com.digitalasset.ledger.example.Transaction.TxDelta
import org.reactivestreams.{Publisher, Subscriber}
import com.digitalasset.ledger.example.Transaction._
import com.digitalasset.platform.sandbox.config.DamlPackageContainer
import com.digitalasset.platform.server.services.command.time.TimeModelValidator
import com.digitalasset.platform.services.time.TimeModel
import org.reactivestreams

import scala.concurrent.duration.FiniteDuration

/**
  * This is an example (simple) in-memory ledger, which comprises most of the ledger api backend.
  * The ledger here is essentially just a list of LedgerSyncEvents.
  * We also maintain an ephemeral cache of active contracts, to efficiently support queries
  * from the backend relating to activeness.
  *
  * @param packages
  * @param timeModel
  * @param timeProvider
  * @param mat
  */
class Ledger(
    packages: DamlPackageContainer,
    timeModel: TimeModel,
    timeProvider: TimeProvider,
    mat: ActorMaterializer) {

  private[this] val lock = new Object()

  private val ec = mat.system.dispatcher
  private val validator = TimeModelValidator(timeModel)

  private var ledger = List.empty[LedgerSyncEvent]
  private var offset = 0
  private var activeContracts = Map.empty[AbsoluteContractId, ContractEntry]
  private var duplicationCheck =
    Set.empty[(String /*ApplicationId*/, CommandId)]
  private var subscriptions = List.empty[Subscription]

  private val heartbeatTask = mat.system.scheduler
    .schedule(
      FiniteDuration(5, TimeUnit.SECONDS),
      FiniteDuration(5, TimeUnit.SECONDS),
      () => publishHeartbeat()
    )(ec)

  def getCurrentLedgerEnd: LedgerSyncOffset = lock.synchronized {
    offset.toString
  }

  def lookupActiveContract(contractId: AbsoluteContractId): Option[ContractEntry] =
    lock.synchronized { activeContracts.get(contractId) }

  def activeContractSetSnapshot(): (LedgerSyncOffset, Map[AbsoluteContractId, ContractEntry]) =
    lock.synchronized { (getCurrentLedgerEnd, activeContracts) }

  def submitAndNotify(submission: TransactionSubmission): Unit = {
    publishEvent(submitToLedger(submission))
  }

  def submitToLedger(submission: TransactionSubmission): LedgerSyncEvent = {

    val recordedAt = timeProvider.getCurrentTime

    // determine inputs and outputs
    val txDelta = Transaction.computeTxDelta(submission)

    // validate transaction
    val mkEvent: LedgerSyncOffset => LedgerSyncEvent =
      validateSubmission(submission, txDelta, recordedAt)

    lock.synchronized {

      val newOffset = advanceLedgerOffset()

      // create an event with this offset
      val event = mkEvent(newOffset)

      // update the ledger
      this.ledger = this.ledger :+ event

      // assign any new outputs absolute contract ids
      val outputs = mkContractOutputs(submission, txDelta, newOffset)

      // prune active contract cache of any consumed inputs and add the outputs
      activeContracts = (activeContracts -- txDelta.inputs) ++ outputs

      // record in duplication check cache
      duplicationCheck = duplicationCheck + ((submission.applicationId, submission.commandId))

      event
    }
  }

  private def advanceLedgerOffset(): LedgerSyncOffset = lock.synchronized {
    this.offset = this.offset + 1
    getCurrentLedgerEnd
  }

  private def validateSubmission(
      submission: TransactionSubmission,
      txDelta: TxDelta,
      recordedAt: Instant): LedgerSyncOffset => LedgerSyncEvent = {

    // check for and ignore duplicates
    if (duplicationCheck.contains((submission.applicationId, submission.commandId))) {
      return offset =>
        mkRejectedCommand(
          DuplicateCommandId("duplicate submission detected"),
          offset,
          submission,
          recordedAt)
    }

    // check for consistency
    // NOTE: we do this by checking the activeness of all input contracts, both
    // consuming and non-consuming.
    if (!txDelta.inputs.subsetOf(activeContracts.keySet) ||
      !txDelta.inputs_nc.subsetOf(activeContracts.keySet)) {
      return offset =>
        mkRejectedCommand(
          Inconsistent("one or more of the inputs has been consumed"),
          offset,
          submission,
          recordedAt)
    }

    // validate transaction
    Validation
      .validate(submission, activeContracts.mapValues(_.contract), packages) match {
      case Validation.Failure(msg) =>
        offset =>
          mkRejectedCommand(Disputed(msg), offset, submission, recordedAt)
      case Validation.Success =>
        offset =>
          mkAcceptedTransaction(offset, submission, recordedAt)
    }
  }

  def ledgerSyncEvents(offset: Option[LedgerSyncOffset]): Publisher[LedgerSyncEvent] =
    new Publisher[LedgerSyncEvent] {
      override def subscribe(subscriber: Subscriber[_ >: LedgerSyncEvent]): Unit =
        lock.synchronized {
          val snapshot = getEventsSnapshot(offset)
          val queue = new LinkedBlockingQueue[LedgerSyncEvent]()
          snapshot.foreach(event => queue.offer(event))
          subscriber.onSubscribe(new reactivestreams.Subscription {
            override def request(n: Long): Unit = ec.execute { () =>
              (1 to n.toInt).foreach { _ =>
                subscriber.onNext(queue.take())
              }
            }
            override def cancel(): Unit =
              throw new UnsupportedOperationException
          })
          subscriptions = subscriptions :+ Subscription(queue, subscriber)
        }
    }

  def shutdownTasks(): Unit = lock.synchronized {
    heartbeatTask.cancel()
    publishHeartbeat()
    for (s <- subscriptions) {
      s.subscriber.onComplete()
    }
  }

  private def publishHeartbeat(): Unit =
    publishEvent(Heartbeat(timeProvider.getCurrentTime, getCurrentLedgerEnd))

  private def publishEvent(event: LedgerSyncEvent): Unit = {
    val subscriptions = lock.synchronized(this.subscriptions)
    for (s <- subscriptions) {
      s.queue.offer(event)
    }
  }

  private def getEventsSnapshot(offset: Option[LedgerSyncOffset]): List[LedgerSyncEvent] =
    lock.synchronized {
      val index: Int = offset match {
        case Some(s) => Integer.parseInt(s)
        case None => 0
      }
      ledger.splitAt(index)._2
    }

  private def mkContractOutputs(
      submission: TransactionSubmission,
      txDelta: TxDelta,
      offset: LedgerSyncOffset): Map[AbsoluteContractId, ContractEntry] =
    txDelta.outputs.toList.map {
      case (contractId, contract) =>
        (
          mkAbsContractId(offset)(contractId),
          ContractEntry(
            contract.contract.mapValue(_.mapContractId(mkAbsContractId(offset))),
            submission.ledgerEffectiveTime,
            offset, // offset used as the transaction id
            submission.workflowId,
            contract.witnesses
          ))
    }.toMap

  private def mkAcceptedTransaction(
      offset: LedgerSyncOffset,
      submission: TransactionSubmission,
      recordedAt: Instant) = {
    val txId = offset // for this ledger, offset is also the transaction id
    AcceptedTransaction(
      toAbsTx(txId, submission.transaction),
      txId,
      Some(submission.submitter),
      submission.ledgerEffectiveTime,
      recordedAt,
      offset,
      submission.workflowId,
      submission.blindingInfo.explicitDisclosure.map {
        case (nid, parties) => (toAbsNodeId(txId, nid), parties)
      },
      Some(submission.applicationId),
      Some(submission.commandId)
    )
  }

  private def mkRejectedCommand(
      rejectionReason: RejectionReason,
      offset: LedgerSyncOffset,
      submission: TransactionSubmission,
      recordedAt: Instant) =
    RejectedCommand(
      recordedAt,
      submission.commandId,
      submission.submitter,
      rejectionReason,
      offset,
      Some(submission.applicationId))

}

/**
  * Every ledger-events subscription has a blocking-queue and a task that continually consumes that
  * queue. To publish an event, we append it to the associated queue.
  * @param queue
  * @param task
  */
case class Subscription(
    queue: BlockingQueue[LedgerSyncEvent],
    subscriber: Subscriber[_ >: LedgerSyncEvent]
)

/**
  * These are the entries in the Active Contract Set.
  * @param contract
  * @param let
  * @param transactionId
  * @param workflowId
  * @param witnesses
  */
case class ContractEntry(
    contract: Value.ContractInst[VersionedValue[AbsoluteContractId]],
    let: Instant,
    transactionId: String,
    workflowId: String,
    witnesses: Set[Ref.Party])
