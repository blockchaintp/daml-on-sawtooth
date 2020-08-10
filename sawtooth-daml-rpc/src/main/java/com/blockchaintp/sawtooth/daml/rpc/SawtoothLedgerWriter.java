package com.blockchaintp.sawtooth.daml.rpc;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import com.blockchaintp.utils.SawtoothClientUtils;
import com.blockchaintp.sawtooth.daml.DamlEngineSingleton;
import com.blockchaintp.sawtooth.daml.Namespace;
import com.blockchaintp.sawtooth.daml.SawtoothDamlUtils;
import com.blockchaintp.sawtooth.daml.protobuf.DamlOperation;
import com.blockchaintp.sawtooth.daml.protobuf.DamlOperationBatch;
import com.blockchaintp.sawtooth.daml.protobuf.DamlTransaction;
import com.blockchaintp.sawtooth.daml.rpc.exception.SawtoothWriteException;
import com.blockchaintp.sawtooth.messaging.ZmqStream;
import com.blockchaintp.utils.KeyManager;
import com.codahale.metrics.SharedMetricRegistries;
import com.daml.ledger.api.health.HealthStatus;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntryId;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlStateKey;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlSubmission;
import com.daml.ledger.participant.state.kvutils.Envelope;
import com.daml.ledger.participant.state.kvutils.KeyValueCommitting;
import com.daml.ledger.participant.state.kvutils.KeyValueSubmission;
import com.daml.ledger.participant.state.kvutils.api.LedgerWriter;
import com.daml.ledger.participant.state.v1.SubmissionResult;
import com.daml.metrics.Metrics;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sawtooth.sdk.messaging.Stream;
import sawtooth.sdk.processor.exceptions.ValidatorConnectionError;
import sawtooth.sdk.protobuf.Batch;
import sawtooth.sdk.protobuf.ClientBatchSubmitResponse;
import sawtooth.sdk.protobuf.ClientBatchSubmitResponse.Status;
import sawtooth.sdk.protobuf.Transaction;
import scala.collection.JavaConverters;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import scala.util.Either;

public final class SawtoothLedgerWriter implements LedgerWriter {

  private static final Logger LOGGER = LoggerFactory.getLogger(SawtoothLedgerWriter.class);

  private static final int DEFAULT_MAX_OPS_PER_BATCH = 10;
  private static final int DEFAULT_MAX_OUTSTANDING_BATCHES = 2;
  private final String participantId;
  private final Metrics metrics;
  private final KeyValueCommitting kvCommitting;
  private final BlockingDeque<CommitPayload> submitQueue;

  private final KeyManager keyManager;

  private final Stream stream;

  private final Submitter submitter;

  private final Thread submitterThread;

  private final int maxOutStandingBatches;

  private final int maxOpsPerBatch;

  public SawtoothLedgerWriter(final String pid, final String zmqUrl, final KeyManager keyMgr) {
    this(pid, new ZmqStream(zmqUrl), keyMgr, DEFAULT_MAX_OPS_PER_BATCH, DEFAULT_MAX_OUTSTANDING_BATCHES);
  }

  public SawtoothLedgerWriter(final String id, final Stream s, final KeyManager k, final int opsPerBatch,
      final int outStandingBatches) {
    this.participantId = id;
    this.stream = s;
    this.keyManager = k;

    this.maxOpsPerBatch = opsPerBatch;
    this.maxOutStandingBatches = outStandingBatches;

    this.submitQueue = new LinkedBlockingDeque<>(this.maxOpsPerBatch + 1);

    String hostname;
    try {
      hostname = InetAddress.getLocalHost().getHostName();
    } catch (final UnknownHostException e) {
      throw new RuntimeException(e);
    }

    this.metrics = new Metrics(SharedMetricRegistries.getOrCreate(hostname));
    this.kvCommitting = new KeyValueCommitting(DamlEngineSingleton.getInstance(), this.metrics);

    new KeyValueSubmission(this.metrics);
    this.submitter = new Submitter(this.stream);
    this.submitterThread = new Thread(this.submitter);
    this.submitterThread.start();
  }

  @Override
  public HealthStatus currentHealth() {
    return HealthStatus.healthy();
  }

  @Override
  public Future<SubmissionResult> commit(final String correlationId, final ByteString envelope) {
    final ByteString logEntryId = makeDamlLogEntryId();
    final List<String> inputAddresses = extractInputAddresses(envelope);
    final List<String> outputAddresses = extractOutputAddresses(logEntryId, envelope);
    final DamlTransaction tx =
        DamlTransaction.newBuilder().setSubmission(envelope).setLogEntryId(logEntryId).build();
    final DamlOperation op = DamlOperation.newBuilder().setCorrelationId(correlationId)
        .setSubmittingParticipant(participantId()).setTransaction(tx).build();
    final CommitPayload cp = new CommitPayload(inputAddresses, outputAddresses, op);

    return Future.apply(() -> {
      try  {
        this.submitQueue.put(cp);
        return SubmissionResult.Acknowledged$.MODULE$;
      } catch (final InterruptedException e) {
        LOGGER.error("Interrupted while submitting transaction", e);
        throw new RuntimeException(e);
      }
    }, ExecutionContext.global());
  }


  private List<String> extractInputAddresses(final ByteString envelope) {
    final Either<String, DamlSubmission> either = Envelope.openSubmission(envelope);
    if (either.isLeft()) {
      throw new RuntimeException(new Exception(either.left().get()));
    }
    final DamlSubmission submission = either.right().get();
    final List<DamlStateKey> inputs = submission.getInputDamlStateList();

    final List<String> addresses = inputs.stream().map(damlStateKey -> {
      return Namespace.makeDamlStateAddress(this.kvCommitting.packDamlStateKey(damlStateKey));
    }).collect(Collectors.toList());
    addresses.add(com.blockchaintp.sawtooth.timekeeper.Namespace.TIMEKEEPER_GLOBAL_RECORD);
    return addresses;
  }

  private List<String> extractOutputAddresses(final ByteString logEntryId,
      final ByteString envelope) {
    final Either<String, DamlSubmission> either = Envelope.openSubmission(envelope);
    if (either.isLeft()) {
      throw new RuntimeException(new Exception(either.left().get()));
    }
    final DamlSubmission submission = either.right().get();
    final DamlLogEntryId entryId = this.kvCommitting.unpackDamlLogEntryId(logEntryId);
    final Collection<DamlStateKey> collStateKeys =
        JavaConverters.asJavaCollection(this.kvCommitting.submissionOutputs(submission));
    return collStateKeys.stream().map(damlStateKey -> {
      return Namespace.makeDamlStateAddress(this.kvCommitting.packDamlStateKey(damlStateKey));
    }).collect(Collectors.toList());
  }

  private ByteString makeDamlLogEntryId() {
    final String uuid = UUID.randomUUID().toString();
    final ByteString entryId = ByteString.copyFromUtf8(uuid);
    return DamlLogEntryId.newBuilder().setEntryId(entryId).build().toByteString();
  }

  @Override
  public String participantId() {
    return this.participantId;
  }

  class CommitPayload {
    private final List<String> inputAddresses;
    private final List<String> outputAddresses;
    private final DamlOperation payload;

    CommitPayload(final List<String> in, final List<String> out,
        final DamlOperation op) {
      this.inputAddresses = ImmutableList.copyOf(in);
      this.outputAddresses = ImmutableList.copyOf(out);
      this.payload = op;
    }

    public DamlOperation getPayload() {
      return payload;
    }

    public List<String> getOutputAddresses() {
      return outputAddresses;
    }

    public List<String> getInputAddresses() {
      return inputAddresses;
    }
  }

  private class Submitter implements Runnable {

    private final Stream stream;
    private volatile boolean keepRunning;

    Submitter(final Stream str) {
      this.stream = str;
      this.keepRunning = true;
    }

    private Transaction accumulatorToTransaction(final List<CommitPayload> accumulator) {
      final Set<String> inputAddressSet = new HashSet<>();
      final Set<String> outputAddressSet = new HashSet<>();
      final List<DamlOperation> batchOps = accumulator.stream().map(commitPayload -> {
        inputAddressSet.addAll(commitPayload.getInputAddresses());
        outputAddressSet.addAll(commitPayload.getOutputAddresses());
        return commitPayload.getPayload();
      }).collect(Collectors.toList());

      final DamlOperationBatch batch =
          DamlOperationBatch.newBuilder().addAllOperations(batchOps).build();
      LOGGER.debug("Added {} ops to batch", batchOps.size());
      final Transaction sawTx = SawtoothDamlUtils.makeSawtoothTransaction(keyManager, batch,
          inputAddressSet, outputAddressSet);
      return sawTx;
    }

    @Override
    public void run() {
      long batchCounter = 0;
      final BlockingDeque<sawtooth.sdk.messaging.Future> outStandingFutures =
          new LinkedBlockingDeque<>(getMaxOutStandingBatches());
      while (keepRunning) {
        CommitPayload cp;
        try {
          cp = submitQueue.poll(1L, TimeUnit.SECONDS);
        } catch (final InterruptedException e) {
          LOGGER.warn("Intterupted while waiting for submissiones");
          keepRunning = false;
          continue;
        }
        final List<CommitPayload> accumulator = new ArrayList<>();
        if (cp != null) {
          LOGGER.trace("Operations to send!");
          accumulator.add(cp);
          submitQueue.drainTo(accumulator,  SawtoothLedgerWriter.this.maxOpsPerBatch - 1);
          LOGGER.trace("Accumulated {} ops", accumulator.size());
        }

        if (accumulator.size() > 0) {
          final Transaction sawTx = accumulatorToTransaction(accumulator);
          batchCounter++;
          LOGGER.debug("Sending batch {} opCount={} ", batchCounter, accumulator.size());
          final Batch sawBatch = SawtoothClientUtils.makeSawtoothBatch(keyManager, List.of(sawTx));
          final sawtooth.sdk.messaging.Future submitBatch =
              SawtoothClientUtils.submitBatch(sawBatch, this.stream);
          boolean accepted = outStandingFutures.offer(submitBatch);
          while (keepRunning && !accepted) {
            try {
              LOGGER.debug("Outstanding Futures count = {}", outStandingFutures.size());
              final Collection<sawtooth.sdk.messaging.Future> checkList = new ArrayList<>();
              outStandingFutures.drainTo(checkList);
              int flushCount = 0;
              for (final sawtooth.sdk.messaging.Future f : checkList) {
                if (f.isDone()) {
                  checkResponse(f);
                  flushCount++;
                } else {
                  outStandingFutures.put(f);
                }
              }
              LOGGER.debug("Flushed {} futures", flushCount);
              if (outStandingFutures.size() == 0 || flushCount > 0) {
                accepted = outStandingFutures.offer(submitBatch);
              }
            } catch (final ValidatorConnectionError e) {
              LOGGER.warn("Received a validator connection error: " + e.getMessage());
              keepRunning = false;
              continue;
            } catch (final InterruptedException e) {
              LOGGER.warn("Interupted while waiting for submissiones");
              keepRunning = false;
              continue;
            } catch (SawtoothWriteException | InvalidProtocolBufferException e) {
              LOGGER.warn("Critical error submitting batch: " + e.getMessage());
              keepRunning = false;
              continue;
            }
          }
        }
      }
    }

    private void checkResponse(final sawtooth.sdk.messaging.Future f) throws InterruptedException,
        ValidatorConnectionError, InvalidProtocolBufferException, SawtoothWriteException {
      final ByteString result = f.getResult();
      final ClientBatchSubmitResponse getResponse = ClientBatchSubmitResponse.parseFrom(result);
      final Status status = getResponse.getStatus();
      switch (status) {
        case OK:
          LOGGER.debug("ClientBatchSubmit response is OK");
          break;
        case QUEUE_FULL:
          LOGGER.warn("ClientBatchSubmit response is QUEUE_FULL");
          break;
        default:
          LOGGER
              .warn("ClientBatchSubmit response is {}", status.toString());
          throw new SawtoothWriteException(
              String.format("ClientBatchSubmit returned %s", getResponse.getStatus()));
      }
    }
  }

  private int getMaxOutStandingBatches() {
    return maxOutStandingBatches;
  }
}