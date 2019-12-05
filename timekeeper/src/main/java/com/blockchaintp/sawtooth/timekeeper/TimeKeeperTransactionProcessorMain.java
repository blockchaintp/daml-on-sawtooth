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
package com.blockchaintp.sawtooth.timekeeper;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.blockchaintp.sawtooth.daml.messaging.ZmqStream;
import com.blockchaintp.sawtooth.timekeeper.processor.TimeKeeperTransactionHandler;
import com.blockchaintp.utils.InMemoryKeyManager;
import com.blockchaintp.utils.KeyManager;

import sawtooth.sdk.messaging.Stream;
import sawtooth.sdk.processor.TransactionHandler;
import sawtooth.sdk.processor.TransactionProcessor;

/**
 * A basic Main class for TimeKeeperTransactionProcessor.
 * @author scealiontach
 */
public final class TimeKeeperTransactionProcessorMain {

  private static final Logger LOGGER = LoggerFactory.getLogger(TimeKeeperTransactionProcessorMain.class);

  /**
   * A basic main method for this transaction processor.
   * @param args at this time only one argument the address of the validator
   *             component endpoint, e.g. tcp://localhost:4004
   */
  public static void main(final String[] args) {
    ScheduledExecutorService clockExecutor = Executors.newSingleThreadScheduledExecutor();

    Stream stream = new ZmqStream(args[0]);
    KeyManager kmgr = InMemoryKeyManager.create();
    final long period = 20;
    final TimeUnit periodUnit = TimeUnit.SECONDS;
    clockExecutor.scheduleWithFixedDelay(new TimeKeeperRunnable(kmgr, stream), period, period, periodUnit);

    TransactionProcessor transactionProcessor = new TransactionProcessor(args[0]);
    TransactionHandler handler = new TimeKeeperTransactionHandler();
    transactionProcessor.addHandler(handler);

    Thread thread = new Thread(transactionProcessor);
    thread.start();
    try {
      thread.join();
      clockExecutor.shutdownNow();
    } catch (InterruptedException exc) {
      LOGGER.warn("TransactionProcessor was interrupted");
    }
  }

  private TimeKeeperTransactionProcessorMain() {
    // private constructor for utility class
  }

}
