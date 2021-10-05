/*
 * Copyright 2021 Blockchain Technology Partners
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
package com.blockchaintp.sawtooth.daml.rpc;

import java.io.IOException;

import com.blockchaintp.keymanager.DirectoryKeyManager;
import com.blockchaintp.keymanager.KeyManager;
import com.daml.ledger.api.health.HealthStatus;
import com.daml.ledger.participant.state.kvutils.Raw;
import com.daml.ledger.participant.state.kvutils.api.CommitMetadata;
import com.daml.ledger.participant.state.kvutils.api.LedgerReader;
import com.daml.ledger.participant.state.kvutils.api.LedgerRecord;
import com.daml.ledger.participant.state.kvutils.api.LedgerWriter;
import com.daml.ledger.participant.state.v1.Offset;
import com.daml.ledger.participant.state.v1.SubmissionResult;
import com.daml.telemetry.TelemetryContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.NotUsed;
import akka.stream.scaladsl.Source;
import scala.Option;
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
   * @param participantId
   *          the participant id
   * @param zmqUrl
   *          the zmq url of the sawtooth node
   * @param k
   *          the path of the keystre
   * @param ledgerId
   *          the ledger id
   */
  public SawtoothReaderWriter(final String participantId, final String zmqUrl, final String k, final String ledgerId) {
    this(participantId, zmqUrl, k, ledgerId, DEFAULT_MAX_OPS_PER_BATCH, DEFAULT_MAX_OUTSTANDING_BATCHES);
  }

  /**
   * Create a SawtoothReaderWriter with the provided parameters.
   *
   * @param participantId
   *          the participant id
   * @param zmqUrl
   *          the zmq url of the sawtooth node
   * @param k
   *          the path of the keystre
   * @param ledgerId
   *          the ledger id
   * @param opsPerBatch
   *          the maximum number per batch
   * @param outstandingBatches
   *          the number of outstanding batches before waiting
   */
  public SawtoothReaderWriter(final String participantId, final String zmqUrl, final String k, final String ledgerId,
      final int opsPerBatch, final int outstandingBatches) {
    this.keystoreDir = k;
    try {
      this.keyMgr = DirectoryKeyManager.create(this.keystoreDir);
    } catch (final IOException e) {
      LOGGER.error("Invalid keystore directory {}", this.keystoreDir);
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

  @Override
  public String participantId() {
    return writer.participantId();
  }

  @Override
  public Future<SubmissionResult> commit(String correlationId, Raw.Envelope envelope, CommitMetadata metadata,
      TelemetryContext telemetryContext) {
    return writer.commit(correlationId, envelope, metadata, telemetryContext);
  }

}
