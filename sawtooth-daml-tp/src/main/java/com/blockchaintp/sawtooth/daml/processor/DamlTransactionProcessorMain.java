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

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.blockchaintp.sawtooth.daml.processor.impl.DamlCommitterImpl;
import com.blockchaintp.sawtooth.daml.processor.impl.DamlTransactionHandler;
import com.digitalasset.daml.lf.engine.Engine;

import sawtooth.sdk.processor.TransactionHandler;

/**
 * A basic Main class for DamlTransactionProcessor.
 *
 * @author scealiontach
 */
public final class DamlTransactionProcessorMain {

  private static final Logger LOGGER = LoggerFactory.getLogger(DamlTransactionProcessorMain.class);

  /**
   * A basic main method for this transaction processor.
   *
   * @param args at this time only one argument the address of the validator
   *             component endpoint, e.g. tcp://localhost:4004
   */
  public static void main(final String[] args) {
    final Engine engine = new Engine();
    String connectStr = null;
    for (final String s : args) {
      if (s.startsWith("-v")) {
        setLoggingLevel(s);
      } else {
        connectStr = s;
      }
    }
    final DamlCommitter committer = new DamlCommitterImpl(engine);
    final TransactionHandler handler = new DamlTransactionHandler(committer);
    final MTTransactionProcessor transactionProcessor = new MTTransactionProcessor(handler, connectStr);
    LOGGER.info("Added handler {}", DamlTransactionHandler.class.getName());
    final Thread thread = new Thread(transactionProcessor);
    thread.start();
    try {
      thread.join();
    } catch (final InterruptedException exc) {
      LOGGER.warn("TransactionProcessor was interrupted");
    }
  }

  private static void setLoggingLevel(final String lvl) {
    int vcount = 0;
    for (int i = 0; i < lvl.length(); i++) {
      if (lvl.charAt(i) == 'v') {
        vcount++;
      }
    }
    if (vcount > 3) {
      vcount = 3;
    }
    switch (vcount) {
      case 1:
        Configurator.setRootLevel(Level.INFO);
        break;
      case 2:
        Configurator.setRootLevel(Level.DEBUG);
        break;
      case 3:
        Configurator.setRootLevel(Level.TRACE);
        break;
      case 0:
      default:
        Configurator.setRootLevel(Level.WARN);
        break;
    }
  }

  private DamlTransactionProcessorMain() {
    // private constructor for utility class
  }

}
