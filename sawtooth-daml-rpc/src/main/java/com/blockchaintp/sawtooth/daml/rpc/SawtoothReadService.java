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
package com.blockchaintp.sawtooth.daml.rpc;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import com.blockchaintp.sawtooth.daml.protobuf.ConfigurationEntry;
import com.blockchaintp.sawtooth.daml.protobuf.ConfigurationMap;
import com.blockchaintp.sawtooth.daml.rpc.events.DamlLogEventHandler;
import com.blockchaintp.sawtooth.daml.util.Namespace;
import com.daml.ledger.participant.state.backport.TimeModel;
import com.daml.ledger.participant.state.v1.Configuration;
import com.daml.ledger.participant.state.v1.LedgerInitialConditions;
import com.daml.ledger.participant.state.v1.Offset;
import com.daml.ledger.participant.state.v1.ReadService;
import com.daml.ledger.participant.state.v1.Update;
import com.digitalasset.daml.lf.data.Time.Timestamp;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import akka.NotUsed;
import akka.stream.scaladsl.Source;
import io.reactivex.Flowable;
import scala.Option;
import scala.Tuple2;

/**
 * Sawtooth implementation of the Daml ReadService.
 */
public class SawtoothReadService implements ReadService {

  private static final Logger LOGGER = Logger.getLogger(SawtoothReadService.class.getName());

  private static final String TIMEMODEL_CONFIG = "com.blockchaintp.sawtooth.daml.timemodel";

  private static final String MAX_TTL_KEY = TIMEMODEL_CONFIG + ".maxTtl";
  private static final String MAX_CLOCK_SKEW_KEY = TIMEMODEL_CONFIG + ".maxClockSkew";
  private static final String MIN_TRANSACTION_LATENCY_KEY = TIMEMODEL_CONFIG + ".minTransactionLatency";

  private static final Timestamp BEGINNING_OF_EPOCH = new Timestamp(0);

  private final String url;
  private final ExecutorService executorService;
  private final String ledgerId;
  private final SawtoothTransactionsTracer trace;
  private boolean startAtTheBeginning = false;

  private DamlLogEventHandler handler;

  /**
   * Build a ReadService based on a zmq address URL.
   * @param thisLedgerId the ledger id for this RPC
   * @param zmqUrl       the url of the zmq endpoint
   */
  public SawtoothReadService(final String thisLedgerId, final String zmqUrl) {
    this.ledgerId = thisLedgerId;
    this.url = zmqUrl;
    this.executorService = Executors.newWorkStealingPool();
    this.trace = null;
    this.handler = new DamlLogEventHandler(this.url);
    this.executorService.submit(this.handler);
  }

  /**
   * Build a ReadService based on a zmq address URL.
   * @param thisLedgerId the ledger id for this RPC
   * @param zmqUrl       the url of the zmq endpoint
   * @param tracer       a transaction tracer
   * @param indexReset   set to true if this reader should start at the first
   *                     offset regardless of subscription. This is useful in the
   *                     case of the in memory reference index server.
   */
  public SawtoothReadService(final String thisLedgerId, final String zmqUrl, final SawtoothTransactionsTracer tracer,
      final boolean indexReset) {
    this.ledgerId = thisLedgerId;
    this.url = zmqUrl;
    this.trace = tracer;
    this.executorService = Executors.newWorkStealingPool();
    this.handler = new DamlLogEventHandler(this.url);
    this.executorService.submit(this.handler);
    this.startAtTheBeginning = indexReset;
  }

  private TimeModel parseTimeModel(final ByteString data) throws InvalidProtocolBufferException {
    ConfigurationMap cm = ConfigurationMap.parseFrom(data);
    Duration maxClockSkew = Duration.ofMinutes(2);
    Duration maxTtl = Duration.ofMinutes(2);
    Duration minTransactionLatency = Duration.ofSeconds(2);
    for (ConfigurationEntry e : cm.getEntriesList()) {
      String key = e.getKey();
      String valString = e.getValue().toStringUtf8();
      if (key.equals(MAX_CLOCK_SKEW_KEY)) {
        maxClockSkew = Duration.parse(valString);
      }
      if (key.equals(MIN_TRANSACTION_LATENCY_KEY)) {
        minTransactionLatency = Duration.parse(valString);
      }
      if (key.equals(MAX_TTL_KEY)) {
        maxTtl = Duration.parse(valString);
      }
    }
    return new TimeModel(minTransactionLatency, maxClockSkew, maxTtl);
  }

  @Override
  public final Source<LedgerInitialConditions, NotUsed> getLedgerInitialConditions() {
    ByteString data = this.handler.getState(Namespace.DAML_CONFIG_TIME_MODEL);

    TimeModel tm;
    if (data == null) {
      LOGGER.info("No time model set on chain using defaults");
      tm = new TimeModel(Duration.ofSeconds(1), Duration.ofMinutes(2), Duration.ofMinutes(2));
    } else {
      // TODO parse time model bs and return;
      try {
        tm = parseTimeModel(data);
      } catch (InvalidProtocolBufferException exc) {
        LOGGER.severe(String.format("Unparseable TimeModel data %s, using defaults", data));
        tm = new TimeModel(Duration.ofSeconds(1), Duration.ofMinutes(2), Duration.ofMinutes(2));
      }
    }
    LOGGER.info(String.format("TimeModel set to %s", tm));
    // TODO ledgerId should be drawn from the ledger
    Flowable<LedgerInitialConditions> f = Flowable.fromArray(new LedgerInitialConditions[] {
        new LedgerInitialConditions(this.ledgerId, new Configuration(tm), BEGINNING_OF_EPOCH)});
    return Source.fromPublisher(f);
  }

  @Override
  public final Source<Tuple2<Offset, Update>, NotUsed> stateUpdates(final Option<Offset> beginAfter) {
    if (beginAfter.isDefined()) {
      LOGGER.info(String.format("Starting event handling at offset=%s", beginAfter.get()));
      this.handler.sendSubscribe(beginAfter.get());
    } else {
      if (this.startAtTheBeginning) {
        LOGGER.info("Starting at the beginning of the chain (offset=1-0) as requested");
        Offset offset=new Offset(new long[] {1,0});
        this.handler.sendSubscribe(offset);
      } else {
        LOGGER.info(String.format("Starting event handling at wherever is current"));
        this.handler.sendSubscribe();
      }
    }
    if (this.trace != null) {
      this.handler.setTracer(this.trace);
    }
    return Source.fromPublisher(this.handler.getPublisher());
  }

}
