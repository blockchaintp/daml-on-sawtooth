/*
 * Copyright 2020 Blockchain Technology Partners
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

package com.blockchaintp.sawtooth.daml.processor;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import com.blockchaintp.sawtooth.SawtoothClientUtils;
import com.blockchaintp.sawtooth.daml.DamlEngineSingleton;
import com.blockchaintp.sawtooth.daml.Namespace;
import com.blockchaintp.sawtooth.daml.exceptions.DamlSawtoothRuntimeException;
import com.blockchaintp.sawtooth.daml.protobuf.DamlOperation;
import com.blockchaintp.sawtooth.daml.protobuf.DamlOperationBatch;
import com.blockchaintp.sawtooth.daml.protobuf.DamlTransaction;
import com.blockchaintp.sawtooth.daml.protobuf.DamlTransactionFragment;
import com.blockchaintp.utils.VersionedEnvelopeUtils;
import com.codahale.metrics.SharedMetricRegistries;
import com.daml.caching.Cache;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntryId;
import com.daml.ledger.validator.SubmissionValidator;
import com.daml.ledger.validator.ValidationFailed;
import com.daml.lf.data.Time.Timestamp;
import com.daml.lf.engine.Engine;
import com.daml.metrics.Metrics;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sawtooth.sdk.processor.Context;
import sawtooth.sdk.processor.TransactionHandler;
import sawtooth.sdk.processor.exceptions.InternalError;
import sawtooth.sdk.processor.exceptions.InvalidTransactionException;
import sawtooth.sdk.protobuf.TpProcessRequest;
import sawtooth.sdk.protobuf.TransactionHeader;
import scala.compat.java8.FutureConverters;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import scala.util.Either;

/**
 * A TransactionHandler implementation which handles DAML.
 *
 * @author scealiontach
 */
public final class DamlTransactionHandler implements TransactionHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(DamlTransactionHandler.class.getName());

  private final String familyName;
  private final String namespace;

  private final String version;

  private final Engine engine;

  private final Metrics metrics;

  /**
   * Constructs a TransactionHandler for DAML Transactions.
   *
   */
  public DamlTransactionHandler() {
    this.namespace = Namespace.getNameSpace();
    this.version = Namespace.DAML_FAMILY_VERSION_1_0;
    this.familyName = Namespace.DAML_FAMILY_NAME;

    this.engine = DamlEngineSingleton.getInstance();
    String hostname;
    try {
      hostname = InetAddress.getLocalHost().getHostName();
    } catch (final UnknownHostException e) {
      throw new DamlSawtoothRuntimeException(e);
    }
    this.metrics = new Metrics(SharedMetricRegistries.getOrCreate(hostname));
  }

  @Override
  public void apply(final TpProcessRequest tpProcessRequest, final Context state)
      throws InvalidTransactionException, InternalError {
    LOGGER.info("Processing transaction {} size={}", tpProcessRequest.getSignature(),
        tpProcessRequest.toByteString().size());
    basicRequestChecks(tpProcessRequest);
    final LedgerState<String> ledgerState = new ContextLedgerState(state);

    try {
      ByteString unwrappedPayload = VersionedEnvelopeUtils.unwrap(tpProcessRequest.getPayload());
      final DamlOperationBatch batch = DamlOperationBatch.parseFrom(unwrappedPayload);
      LOGGER.info("Processing {} operations for {}", batch.getOperationsList().size(), tpProcessRequest.getSignature());
      for (final DamlOperation operation : batch.getOperationsList()) {
        final String participantId = operation.getSubmittingParticipant();
        if (operation.hasTransaction()) {
          final DamlTransaction tx = operation.getTransaction();
          handleTransaction(ledgerState, tx, participantId, operation.getCorrelationId());
        } else if (operation.hasLargeTransaction()) {
          final DamlTransactionFragment tx = operation.getLargeTransaction();
          handleLargeTransaction(ledgerState, tx, participantId, operation.getCorrelationId());
        } else {
          LOGGER.warn("DamlOperation contains no supported operation, ignoring ...");
        }
      }
      ledgerState.flushDeferredEvents();
      LOGGER.info("Completed {} operations for {}", batch.getOperationsList().size(), tpProcessRequest.getSignature());
    } catch (final InvalidProtocolBufferException ipbe) {
      throw new InvalidTransactionException(
          String.format("Payload is unparseable and not a valid DamlSubmission %s", ipbe.getMessage()));
    }
  }

  private void handleLargeTransaction(final LedgerState<String> ledgerState, final DamlTransactionFragment ltx,
      final String participantId, final String correlationId) throws InvalidTransactionException, InternalError {
    if (ltx.getPartNumber() != ltx.getParts()) {
      LOGGER.warn("Storing transaction fragment part {} of {} size={}", ltx.getPartNumber(), ltx.getParts(),
          ltx.getSubmissionFragment().size());
      ledgerState.storeTransactionFragmet(ltx);
    } else {
      LOGGER.warn("Assembling and handling transaction with fragment part {} of {} size={}", ltx.getPartNumber(),
          ltx.getParts(), ltx.getSubmissionFragment().size());
      final DamlTransaction tx = ledgerState.assembleTransactionFragments(ltx);
      handleTransaction(ledgerState, tx, participantId, correlationId);
    }
  }

  private String handleTransaction(final LedgerState<String> ledgerState, final DamlTransaction tx,
      final String participantId, final String correlationId) throws InvalidTransactionException, InternalError {
    DamlLogEntryId logEntryId;
    try {
      logEntryId = DamlLogEntryId.parseFrom(tx.getLogEntryId());
    } catch (final InvalidProtocolBufferException e1) {
      LOGGER.warn("InvalidProtocolBufferException when parsing log entry id");
      throw new InvalidTransactionException(e1.getMessage());
    }
    final ExecutionContext ec = ExecutionContext.fromExecutor(ExecutionContext.global());
    final SubmissionValidator<String> validator = SubmissionValidator.create(ledgerState, () -> logEntryId, false,
        Cache.none(), this.engine, this.metrics, ec);
    final Timestamp recordTime = ledgerState.getRecordTime();
    LOGGER.trace("Begin validation correlationId={}", correlationId);
    final Future<Either<ValidationFailed, String>> validateAndCommit = validator.validateAndCommit(tx.getSubmission(),
        correlationId, recordTime, participantId);
    final CompletionStage<Either<ValidationFailed, String>> validateAndCommitCS = FutureConverters
        .toJava(validateAndCommit);
    LOGGER.trace("End validation correlationId={}", correlationId);
    try {
      final Either<ValidationFailed, String> either = validateAndCommitCS.toCompletableFuture().get();
      if (either.isLeft()) {
        final ValidationFailed validationFailed = either.left().get();
        try {
          LOGGER.info("ValidationFailed tx={}", JsonFormat.printer().includingDefaultValueFields().print(tx));
        } catch (InvalidProtocolBufferException ipbe) {
          LOGGER.info("ValidationFailed buffer is invalid tx={}", tx);
        }
        throw new InvalidTransactionException(validationFailed.toString());
      } else {
        final String logId = either.right().get();
        LOGGER.trace("Processed transaction into log event logId={} correlationId={}", logId, correlationId);
        return logId;
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new InternalError("Interrupted while handling transaction" + e.getMessage());
    } catch (ExecutionException e) {
      throw new InternalError("While handling transaction: " + e.getMessage());
    }
  }

  /**
   * Fundamental checks of the transaction.
   *
   * @param tpProcessRequest the process request
   * @throws InvalidTransactionException if the transaction fails because of a
   *                                     business rule validation error
   * @throws InternalError               if the transaction fails because of a
   *                                     system error
   */
  private void basicRequestChecks(final TpProcessRequest tpProcessRequest) throws InvalidTransactionException {

    final TransactionHeader header = tpProcessRequest.getHeader();
    if (header == null) {
      throw new InvalidTransactionException("Header expected");
    }

    final ByteString payload = tpProcessRequest.getPayload();
    if (payload.size() == 0) {
      throw new InvalidTransactionException("Empty payload");
    }

    if (!header.getFamilyName().equals(this.familyName)) {
      throw new InvalidTransactionException("Family name does not match");
    }

    if (!header.getFamilyVersion().contentEquals(this.version)) {
      throw new InvalidTransactionException("Version does not match");
    }
  }

  @Override
  public Collection<String> getNameSpaces() {
    return Arrays.asList(this.namespace);
  }

  @Override
  public String getVersion() {
    return this.version;
  }

  @Override
  public String transactionFamilyName() {
    return this.familyName;
  }

}
