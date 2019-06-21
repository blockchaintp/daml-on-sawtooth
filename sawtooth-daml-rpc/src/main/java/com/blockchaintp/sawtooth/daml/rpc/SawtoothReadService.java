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
import java.util.concurrent.TimeoutException;
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
import sawtooth.sdk.messaging.Future;
import sawtooth.sdk.messaging.Stream;
import sawtooth.sdk.messaging.ZmqStream;
import sawtooth.sdk.processor.exceptions.ValidatorConnectionError;
import sawtooth.sdk.protobuf.ClientStateGetRequest;
import sawtooth.sdk.protobuf.ClientStateGetResponse;
import sawtooth.sdk.protobuf.Message.MessageType;
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

  private static final long DEFAULT_TIMEOUT = 0;

  private final String url;
  private final ExecutorService executorService;
  private final String ledgerId;
  private final SawtoothTransactionsTracer trace;

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
  }

  /**
   * Build a ReadService based on a zmq address URL.
   * @param thisLedgerId the ledger id for this RPC
   * @param zmqUrl       the url of the zmq endpoint
   * @param tracer       a transaction tracer
   */
  public SawtoothReadService(final String thisLedgerId, final String zmqUrl, final SawtoothTransactionsTracer tracer) {
    this.ledgerId = thisLedgerId;
    this.url = zmqUrl;
    this.trace = tracer;
    this.executorService = Executors.newWorkStealingPool();
  }

  @Override
  public final Source<LedgerInitialConditions, NotUsed> getLedgerInitialConditions() {
    // TODO this should be fetched from the chain
    
    TimeModel tm = getTimeModel();
    if ( tm == null ) {
      LOGGER.info("No time model set on chain using defaults");
        tm=new TimeModel(Duration.ofSeconds(1), Duration.ofMinutes(2), Duration.ofMinutes(2));
    }
    LOGGER.info(String.format("TimeModel set to %s",tm));
    Flowable<LedgerInitialConditions> f = Flowable.fromArray(new LedgerInitialConditions[] {
        new LedgerInitialConditions(this.ledgerId, new Configuration(tm), BEGINNING_OF_EPOCH)});
    return Source.fromPublisher(f);
  }

  @Override
  public final Source<Tuple2<Offset, Update>, NotUsed> stateUpdates(final Option<Offset> beginAfter) {
    DamlLogEventHandler dleHandler;
    if (beginAfter.isDefined()) {
      LOGGER.info(String.format("Starting event handling at offset=%s", beginAfter.get()));
      dleHandler = new DamlLogEventHandler(url, beginAfter.get());
    } else {
      LOGGER.info(String.format("Starting event handling at wherever is current"));
      dleHandler = new DamlLogEventHandler(url);
    }
    if (this.trace != null) {
      dleHandler.setTracer(this.trace);
    }
    executorService.submit(dleHandler);
    return Source.fromPublisher(dleHandler.getPublisher());
  }

  private TimeModel getTimeModel() {
    try (Stream stream = new ZmqStream(this.url)) {

      ClientStateGetRequest req = ClientStateGetRequest.newBuilder().setAddress(Namespace.DAML_CONFIG_TIME_MODEL)
          .build();
      Future resp = stream.send(MessageType.CLIENT_STATE_GET_REQUEST, req.toByteString());

      LOGGER.info(
          String.format("Waiting for ClientStateGetResponse for TimeModel at %s", Namespace.DAML_CONFIG_TIME_MODEL));
      try {
        ByteString result = null;
        while (result == null) {
          try {
            result = resp.getResult(DEFAULT_TIMEOUT);
            ClientStateGetResponse response = ClientStateGetResponse.parseFrom(result);
            switch (response.getStatus()) {
            case OK:
              LOGGER.info("ClientStateGetResponse received...");

              ByteString bs = response.getValue();
              ConfigurationMap cm = ConfigurationMap.parseFrom(bs);
              Duration maxTtl = null;
              Duration maxClockSkew = null;
              Duration minTxLatency = null;

              for (ConfigurationEntry ce : cm.getEntriesList()) {
                if (ce.getKey().equals(MIN_TRANSACTION_LATENCY_KEY)) {
                  minTxLatency = Duration.parse(ce.getValue().toStringUtf8());
                }
                if (ce.getKey().equals(MAX_CLOCK_SKEW_KEY)) {
                  maxClockSkew = Duration.parse(ce.getValue().toStringUtf8());
                }
                if (ce.getKey().equals(MAX_TTL_KEY)) {
                  maxTtl = Duration.parse(ce.getValue().toStringUtf8());
                }
              }
              if (null == maxTtl || null == maxClockSkew || null == minTxLatency) {
                return null;
              } else {
                return new TimeModel(minTxLatency, maxClockSkew, maxTtl);
              }
            case INVALID_ADDRESS:
            case NOT_READY:
            case NO_ROOT:
            case INTERNAL_ERROR:
            case NO_RESOURCE:
            case UNRECOGNIZED:
            case STATUS_UNSET:
            case INVALID_ROOT:
            default:
              LOGGER.severe(
                  String.format("Invlid response received from ClientBlockGetByNumRequest: %s", response.getStatus()));
            }
          } catch (TimeoutException exc) {
            LOGGER.warning("Still waiting for ClientBlockGetResponse...");
          }
        }
      } catch (InterruptedException | InvalidProtocolBufferException | ValidatorConnectionError exc) {
        LOGGER.warning(exc.getMessage());
      }
    } catch (Exception exc1) {
      LOGGER.info(String.format("Exception closing stream: %s", exc1.getMessage()));
    }
    return null;
  }

}
