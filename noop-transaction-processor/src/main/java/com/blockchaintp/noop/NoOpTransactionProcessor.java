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
package com.blockchaintp.noop;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sawtooth.sdk.processor.TransactionProcessor;

/**
 * A transaction processor which blindly accepts or denies transactions without
 * regard to their contents.
 * @author scealiontach
 */
public class NoOpTransactionProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(NoOpTransactionProcessor.class);

  private static final int ACCEPT_ALL = 1;
  @SuppressWarnings("unused")
  private static final int INVALID_ALL = 2;
  private static final int ERROR_ALL = 3;

  protected NoOpTransactionProcessor() {

  }

  private static CommandLine parseArgs(final String... args) throws ParseException {
    Options options = new Options();
    Option validatorOption = Option.builder("c").required(true).longOpt("connect").hasArg().type(String.class)
        .argName("validator-address").desc("Address and port to connect to the validator, e.g. tcp://localhost:4004")
        .build();
    Option familyNameOption = Option.builder("n").required(true).longOpt("family-name").hasArg().type(String.class)
        .argName("family-name").desc("Family Name which this TP will handle").build();
    Option familyVersionOption = Option.builder("f").required().longOpt("family-version").hasArg().type(String.class)
        .argName("family-version").desc("Version of the family this TP will handle").build();
    Option strategyOption = Option.builder("s").required(false).longOpt("strategy").argName("noop-strategy")
        .type(Integer.class)
        .desc("1=Accept All transactions, 2=Reject all transactions, 3=InternalError all transactions").build();
    options.addOption(validatorOption).addOption(familyNameOption).addOption(familyVersionOption)
        .addOption(strategyOption);
    CommandLineParser parser = new DefaultParser();
    return parser.parse(options, args);
  }

  /**
   * The usual main method.
   * @param args execute via the command line for argument syntax
   */
  public static void main(final String[] args) {
    try {
      CommandLine cli = parseArgs(args);
      String validatorAddress = cli.getOptionValue("validator-address");
      TransactionProcessor processor = new TransactionProcessor(validatorAddress);

      String namespace = cli.getOptionValue("family-namespace", "noop");
      String version = cli.getOptionValue("family-version", "1.0");

      Integer strategy = (Integer) cli.getParsedOptionValue("noop-strategy");
      if (strategy > ERROR_ALL || strategy < ACCEPT_ALL) {
        throw new RuntimeException("Strategy must be 1, 2, or 3");
      }

      NoOpTransactionHandler transactionHandler = new NoOpTransactionHandler(namespace, version, strategy);
      processor.addHandler(transactionHandler);
      ExecutorService pool = Executors.newSingleThreadExecutor();
      Future<?> submit = pool.submit(processor);
      submit.get();

    } catch (ParseException e) {
      LOGGER.error("Error parsing command", e);
    } catch (InterruptedException exc) {
      LOGGER.warn("Interrupted while waiting for processing to complete", exc);
    } catch (ExecutionException exc) {
      LOGGER.warn("Failed to start processor", exc);
    }
  }
}
