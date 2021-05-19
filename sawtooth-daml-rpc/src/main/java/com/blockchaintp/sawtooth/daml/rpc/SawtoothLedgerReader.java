package com.blockchaintp.sawtooth.daml.rpc;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.daml.ledger.api.health.HealthStatus;
import com.daml.ledger.participant.state.kvutils.api.LedgerReader;
import com.daml.ledger.participant.state.kvutils.api.LedgerRecord;
import com.daml.ledger.participant.state.v1.Offset;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.NotUsed;
import akka.stream.scaladsl.Source;
import scala.Option;

/**
 * A Sawtooth based LedgerReader suitable for use in DAML Apis.
 */
public final class SawtoothLedgerReader implements LedgerReader {

  private static final Logger LOGGER = LoggerFactory.getLogger(SawtoothLedgerReader.class.getName());

  private final String ledgerId;
  private final ZmqEventHandler handler;
  private final ExecutorService executorService;

  /**
   * Create a SawtoothLedgerReader with the given ledger id and zmq url.
   * @param lid the ledger id
   * @param zmqUrl the url of the sawtooth node
   */
  public SawtoothLedgerReader(final String lid, final String zmqUrl) {
    this.executorService = Executors.newWorkStealingPool();
    this.ledgerId = lid;
    this.handler = new ZmqEventHandler(zmqUrl);
    this.executorService.submit(this.handler);
  }

  @Override
  public HealthStatus currentHealth() {
    return HealthStatus.healthy();
  }

  @Override
  public Source<LedgerRecord, NotUsed> events(final Option<Offset> startExclusive) {
    final Publisher<LedgerRecord> publisher = this.handler.makePublisher();
    if (startExclusive.isDefined()) {
      LOGGER.info("Starting event handling at offset={}", startExclusive.get());
      this.handler.sendSubscribe(startExclusive.get());
    } else {
      LOGGER.info("Starting event handling at wherever is current");
      this.handler.sendSubscribe();
    }

    return Source.fromPublisher(publisher);
  }

  @Override
  public String ledgerId() {
    return this.ledgerId;
  }

}
