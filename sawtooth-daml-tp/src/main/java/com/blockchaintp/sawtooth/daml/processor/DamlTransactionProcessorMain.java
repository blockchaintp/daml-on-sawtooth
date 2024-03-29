/*
 *  Copyright © 2023 Paravela Limited
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.blockchaintp.sawtooth.daml.processor;

import com.blockchaintp.sawtooth.processor.MTTransactionProcessor;
import com.blockchaintp.utils.LogUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sawtooth.sdk.processor.TransactionHandler;

/**
 * A basic Main class for DamlTransactionProcessor.
 *
 * @author scealiontach
 */
public final class DamlTransactionProcessorMain {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(DamlTransactionProcessorMain.class.getName());

  /**
   * A basic main method for this transaction processor.
   *
   * @param args at this time only one argument the address of the validator component endpoint,
   *             e.g. tcp://localhost:4004
   */
  public static void main(final String[] args) {
    var vCount = 0;
    var connectStr = "tcp://localhost:4004";
    for (String s : args) {
      if (s.startsWith("-v")) {
        for (var i = 0; i < s.length(); i++) {
          if (s.charAt(i) == 'v') {
            vCount++;
          }
        }
      } else {
        connectStr = s;
      }
    }
    LogUtils.setRootLogLevel(vCount);
    TransactionHandler handler = new DamlTransactionHandler();
    MTTransactionProcessor transactionProcessor = new MTTransactionProcessor(handler, connectStr);
    LOGGER.info("Added handler {}", DamlTransactionHandler.class.getName());
    var thread = new Thread(transactionProcessor);
    thread.start();
    try {
      thread.join();
    } catch (InterruptedException exc) {
      LOGGER.warn("TransactionProcessor was interrupted");
      Thread.currentThread().interrupt();
    }
  }

  private DamlTransactionProcessorMain() {
    // private constructor for utility class
  }

}
