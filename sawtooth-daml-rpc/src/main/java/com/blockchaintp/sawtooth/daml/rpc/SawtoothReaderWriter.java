package com.blockchaintp.sawtooth.daml.rpc;

import java.io.IOException;

import com.blockchaintp.utils.DirectoryKeyManager;
import com.blockchaintp.utils.KeyManager;
import com.daml.ledger.api.health.HealthStatus;
import com.daml.ledger.participant.state.kvutils.api.LedgerReader;
import com.daml.ledger.participant.state.kvutils.api.LedgerRecord;
import com.daml.ledger.participant.state.kvutils.api.LedgerWriter;
import com.daml.ledger.participant.state.v1.Offset;
import com.daml.ledger.participant.state.v1.SubmissionResult;
import com.daml.resources.Resource;
import com.daml.resources.ResourceOwner;
import com.google.protobuf.ByteString;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.NotUsed;
import akka.stream.scaladsl.Source;
import scala.Option;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;

/**
 * A delegating class for sawtooth based LedgerReader and LedgerWriter.
 */
public final class SawtoothReaderWriter implements LedgerReader, LedgerWriter {

  private static final Logger LOGGER = LoggerFactory.getLogger(SawtoothReaderWriter.class.getName());

  private static final int DEFAULT_MAX_OPS_PER_BATCH = 1000;
  private static final int DEFAULT_MAX_OUTSTANDING_BATCHES = 1;

  private final SawtoothLedgerReader reader;
  private final SawtoothLedgerWriter writer;
  private final String keystoreDir;
  private KeyManager keyMgr;

  /**
   * Create a SawtoothReaderWriter with the provided parameters.
   *
   * @param participantId the participant id
   * @param zmqUrl the zmq url of the sawtooth node
   * @param k the path of the keystre
   * @param ledgerId the ledger id
   */
  public SawtoothReaderWriter(final String participantId, final String zmqUrl,
      final String k, final String ledgerId) {
    this(participantId, zmqUrl, k, ledgerId, DEFAULT_MAX_OPS_PER_BATCH,
        DEFAULT_MAX_OUTSTANDING_BATCHES);
  }

  /**
   * Create a SawtoothReaderWriter with the provided parameters.
   *
   * @param participantId the participant id
   * @param zmqUrl the zmq url of the sawtooth node
   * @param k the path of the keystre
   * @param ledgerId the ledger id
   * @param opsPerBatch the maximum number per batch
   * @param outstandingBatches the number of outstanding batches before waiting
   */
  public SawtoothReaderWriter(final String participantId, final String zmqUrl,
      final String k, final String ledgerId, final int opsPerBatch, final int outstandingBatches) {
    this.keystoreDir = k;
    try {
      this.keyMgr = DirectoryKeyManager.create(this.keystoreDir);
    } catch (final IOException e) {
      LOGGER.error("Invalid keystore directory " + this.keystoreDir);
      throw new IllegalArgumentException(e);
    }
    this.reader = new SawtoothLedgerReader(ledgerId, zmqUrl);
    this.writer = new SawtoothLedgerWriter(participantId, zmqUrl, keyMgr, opsPerBatch, outstandingBatches);
  }

  @Override
  public HealthStatus currentHealth() {
    if (reader.currentHealth().equals(HealthStatus.unhealthy())) {
      return HealthStatus.unhealthy();
    } else if (writer.currentHealth().equals(HealthStatus.unhealthy())) {
      return HealthStatus.unhealthy();
    } else {
      return HealthStatus.healthy();
    }
  }

  @Override
  public Source<LedgerRecord, NotUsed> events(final Option<Offset> startExclusive) {
    return reader.events(startExclusive);
  }

  @Override
  public String ledgerId() {
    return reader.ledgerId();
  }

  @Deprecated
  @Override
  public Future<SubmissionResult> commit(final String correlationId, final ByteString envelope) {
    return writer.commit(correlationId, envelope);
  }

  @Override
  public String participantId() {
    return writer.participantId();
  }

  /**
   * A resource owner suitable for DAML apis.
   */
  public static final class Owner implements ResourceOwner<SawtoothReaderWriter> {

    private final String participantId;
    private final String zmqUrl;
    private final String keystore;
    private final String ledgerId;
    private int outstandingBatches;
    private int opsPerBatch;

    /**
   * A resource owner suitable for DAML apis.
     *
     * @param configuredParticipantId the participant id
     * @param z the zmqUrl
     * @param k the keystore location
     * @param cfgOpsPerBatch the max number of operations per batch
     * @param cfgOutstandingBatches the maximum number of batches to submit before waiting.
     * @param lid the ledger id
     */
    public Owner(final String configuredParticipantId, final String z, final String k,
        final int cfgOpsPerBatch, final int cfgOutstandingBatches, final Option<String> lid) {
      this.participantId = configuredParticipantId;
      this.zmqUrl = z;
      this.keystore = k;
      this.opsPerBatch = cfgOpsPerBatch;
      this.outstandingBatches = cfgOutstandingBatches;
      this.ledgerId = lid.getOrElse(() -> "default-ledger-id");

    }

    @Override
    public Resource<SawtoothReaderWriter> acquire(final ExecutionContext executionContext) {
      return Resource.successful(new SawtoothReaderWriter(this.participantId, this.zmqUrl,
          this.keystore, this.ledgerId, this.opsPerBatch, this.outstandingBatches),
          executionContext);
    }

  }
}
