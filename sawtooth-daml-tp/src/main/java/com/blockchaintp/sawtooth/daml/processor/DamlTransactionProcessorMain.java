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
package com.blockchaintp.sawtooth.daml.processor;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.blockchaintp.sawtooth.daml.processor.impl.DamlCommitterImpl;
import com.blockchaintp.sawtooth.daml.processor.impl.DamlTransactionHandler;
import com.digitalasset.daml.lf.engine.Engine;

import sawtooth.sdk.processor.TransactionHandler;

/**
 * A basic Main class for DamlTransactionProcessor.
 * @author scealiontach
 */
public final class DamlTransactionProcessorMain {

  private static final Logger LOGGER = Logger.getLogger(DamlTransactionProcessorMain.class.getName());

  /**
   * A basic main method for this transaction processor.
   * @param args at this time only one argument the address of the validator
   *             component endpoint, e.g. tcp://localhost:4004
   */
  public static void main(final String[] args) {
    Logger.getGlobal().setLevel(Level.ALL);
    Engine engine = new Engine();
    DamlCommitter committer = new DamlCommitterImpl(engine);
    TransactionHandler handler = new DamlTransactionHandler(committer);
    MTTransactionProcessor transactionProcessor = new MTTransactionProcessor(handler, args[0]);
    LOGGER.info(String.format("Added handler %s", DamlTransactionHandler.class.getName()));
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
