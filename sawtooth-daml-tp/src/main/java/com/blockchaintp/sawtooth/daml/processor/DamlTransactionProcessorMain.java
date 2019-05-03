package com.blockchaintp.sawtooth.daml.processor;

import java.util.logging.Logger;

import com.blockchaintp.sawtooth.daml.processor.impl.DamlCommitterImpl;
import com.blockchaintp.sawtooth.daml.processor.impl.DamlTransactionHandler;
import com.digitalasset.daml.lf.engine.Engine;

import sawtooth.sdk.processor.TransactionHandler;
import sawtooth.sdk.processor.TransactionProcessor;

public final class DamlTransactionProcessorMain {

  private static final Logger LOGGER = Logger.getLogger(DamlTransactionProcessorMain.class.getName());

  public static void main(final String[] args) {
    TransactionProcessor transactionProcessor = new TransactionProcessor(args[0]);
    Engine engine = new Engine();
    DamlCommitter committer = new DamlCommitterImpl(engine);
    TransactionHandler handler = new DamlTransactionHandler(committer);
    transactionProcessor.addHandler(handler);
    Thread thread = new Thread(transactionProcessor);
    thread.start();
    try {
      thread.join();
    } catch (InterruptedException exc) {
      LOGGER.warning("TransactionProcessor was interrupted");
    }
  }

  private DamlTransactionProcessorMain() {
    // private constructor for utility class
  }

}
