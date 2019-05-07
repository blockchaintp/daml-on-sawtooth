package com.blockchaintp.sawtooth.daml.rpc;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.blockchaintp.sawtooth.daml.rpc.events.DamlLogEventHandler;
import com.daml.ledger.participant.state.v1.LedgerInitialConditions;
import com.daml.ledger.participant.state.v1.Offset;
import com.daml.ledger.participant.state.v1.ReadService;
import com.daml.ledger.participant.state.v1.Update;

import akka.NotUsed;
import akka.stream.scaladsl.Source;
import scala.Option;
import scala.Tuple2;

/**
 * Sawtooth implementation of the Daml ReadService.
 */
public class SawtoothReadService implements ReadService {

  private String url;
  private ExecutorService executorService;

  /**
   * Build a ReadService based on a zmq address URL.
   * @param zmqUrl the url of the zmq endpoint
   */
  public SawtoothReadService(final String zmqUrl) {
    this.url = zmqUrl;
    executorService = Executors.newWorkStealingPool();
  }

  @Override
  public final Source<LedgerInitialConditions, NotUsed> getLedgerInitialConditions() {
    // TODO Auto-generated method stub

    return null;
  }

  @Override
  public final Source<Tuple2<Offset, Update>, NotUsed> stateUpdates(final Option<Offset> beginAfter) {

    if (beginAfter.isDefined()) {
      // then we create a catch-up processor for this guy
      return null;
    } else {
      DamlLogEventHandler dleHandler = new DamlLogEventHandler(url);
      executorService.submit(dleHandler);
      return Source.fromPublisher(dleHandler.getPublisher());
    }
  }

}
