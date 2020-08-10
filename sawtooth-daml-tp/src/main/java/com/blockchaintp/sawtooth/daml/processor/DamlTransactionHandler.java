/*
 *  Copyright 2020 Blockchain Technology Partners
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.blockchaintp.sawtooth.daml.processor;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import com.blockchaintp.sawtooth.daml.DamlEngineSingleton;
import com.blockchaintp.sawtooth.daml.Namespace;
import com.blockchaintp.sawtooth.daml.protobuf.DamlOperation;
import com.blockchaintp.sawtooth.daml.protobuf.DamlOperationBatch;
import com.blockchaintp.sawtooth.daml.protobuf.DamlTransaction;
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
      throw new RuntimeException(e);
    }
    this.metrics = new Metrics(SharedMetricRegistries.getOrCreate(hostname));
  }

  @Override
  public void apply(final TpProcessRequest tpProcessRequest, final Context state)
      throws InvalidTransactionException, InternalError {
    LOGGER.debug("Processing transaction {}", tpProcessRequest.getSignature());
    basicRequestChecks(tpProcessRequest);
    final LedgerState<String> ledgerState = new ContextLedgerState(state);

    try {
      final DamlOperationBatch batch = DamlOperationBatch.parseFrom(tpProcessRequest.getPayload());
      for (final DamlOperation operation : batch.getOperationsList()) {
        final String participantId = operation.getSubmittingParticipant();
        if (operation.hasTransaction()) {
          final DamlTransaction tx = operation.getTransaction();
          handleTransaction(ledgerState, tx, participantId, operation.getCorrelationId());
        } else {
          LOGGER.debug("DamlOperation contains no supported operation, ignoring ...");
        }
      }
    } catch (final InvalidProtocolBufferException ipbe) {
      LOGGER.error("Failed to parse DamlSubmission protocol buffer:");
      throw new RuntimeException(
          String.format("Payload is unparseable and not a valid DamlSubmission %s",
              ipbe.getMessage().getBytes(Charset.defaultCharset())),
          ipbe);
    }
  }

  private String handleTransaction(final LedgerState<String> ledgerState, final DamlTransaction tx,
      final String participantId, final String correlationId) throws InvalidTransactionException,
      InternalError {
    DamlLogEntryId logEntryId;
    try {
      logEntryId = DamlLogEntryId.parseFrom(tx.getLogEntryId());
    } catch (final InvalidProtocolBufferException e1) {
      LOGGER.warn("InvalidProtocolBufferException when parsing log entry id");
      throw new InvalidTransactionException(e1.getMessage());
    }
    final ExecutionContext ec = ExecutionContext.fromExecutor(ExecutionContext.global());
    final SubmissionValidator<String> validator = SubmissionValidator.create(ledgerState, () -> {
      return logEntryId;
    }, false, Cache.none(), this.engine, this.metrics, ec);
    final Timestamp recordTime = ledgerState.getRecordTime();
    final Future<Either<ValidationFailed, String>> validateAndCommit =
        validator.validateAndCommit(tx.getSubmission(), correlationId, recordTime, participantId);
    final CompletionStage<Either<ValidationFailed, String>> validateAndCommitCS =
        FutureConverters.toJava(validateAndCommit);
    try {
      final Either<ValidationFailed, String> either =
          validateAndCommitCS.toCompletableFuture().get();
      if (either.isLeft()) {
        final ValidationFailed validationFailed = either.left().get();
        throw new InvalidTransactionException(validationFailed.toString());
      } else {
        final String logId = either.right().get();
        LOGGER.info("Processed transaction into log event {}", logId);
        return logId;
      }
    } catch (InterruptedException | ExecutionException e) {
      throw new InternalError(e.getMessage());
    }
  }

  /**
   * Fundamental checks of the transaction.
   *
   * @param tpProcessRequest the process request
   * @throws InvalidTransactionException if the transaction fails because of a business rule
   *                                     validation error
   * @throws InternalError               if the transaction fails because of a system error
   */
  private void basicRequestChecks(final TpProcessRequest tpProcessRequest)
      throws InvalidTransactionException, InternalError {

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
    return Arrays.asList(new String[] {this.namespace});
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