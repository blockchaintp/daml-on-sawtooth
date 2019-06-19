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

import com.blockchaintp.sawtooth.daml.rpc.events.DamlLogEventHandler;
import com.daml.ledger.participant.state.backport.TimeModel;
import com.daml.ledger.participant.state.v1.Configuration;
import com.daml.ledger.participant.state.v1.LedgerInitialConditions;
import com.daml.ledger.participant.state.v1.Offset;
import com.daml.ledger.participant.state.v1.ReadService;
import com.daml.ledger.participant.state.v1.Update;
import com.digitalasset.daml.lf.data.Time.Timestamp;

import akka.NotUsed;
import akka.stream.scaladsl.Source;
import io.reactivex.Flowable;
import scala.Option;
import scala.Tuple2;

/**
 * Sawtooth implementation of the Daml ReadService.
 */
public class SawtoothReadService implements ReadService {

  private static final Timestamp BEGINNING_OF_EPOCH = new Timestamp(0);
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
    TimeModel tm = new TimeModel(Duration.ofSeconds(1), Duration.ofMinutes(2), Duration.ofMinutes(2));
    Flowable<LedgerInitialConditions> f = Flowable.fromArray(new LedgerInitialConditions[] {
        new LedgerInitialConditions(this.ledgerId, new Configuration(tm), BEGINNING_OF_EPOCH)});
    return Source.fromPublisher(f);
  }

  @Override
  public final Source<Tuple2<Offset, Update>, NotUsed> stateUpdates(final Option<Offset> beginAfter) {
    if (beginAfter.isDefined()) {
      // then we create a catch-up processor for this guy
      return null;
    } else {
      DamlLogEventHandler dleHandler = new DamlLogEventHandler(url);
      if (this.trace != null) {
        dleHandler.setTracer(this.trace);
      }
      executorService.submit(dleHandler);
      return Source.fromPublisher(dleHandler.getPublisher());
    }
  }

}
