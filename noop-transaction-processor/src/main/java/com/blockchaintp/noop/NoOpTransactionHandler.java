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

import java.io.UnsupportedEncodingException;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sawtooth.sdk.processor.Context;
import sawtooth.sdk.processor.TransactionHandler;
import sawtooth.sdk.processor.Utils;
import sawtooth.sdk.processor.exceptions.InternalError;
import sawtooth.sdk.processor.exceptions.InvalidTransactionException;
import sawtooth.sdk.protobuf.TpProcessRequest;

/**
 * The NoOpTransaction handler is useful for testing purposes only. It will
 * either blindly accept or blindly reject transactions for a given transaction
 * family and version depending on its construction.
 */
public final class NoOpTransactionHandler implements TransactionHandler {

  private static final int NAMESPACE_LENGTH = 6;

  private static final Logger LOG = LoggerFactory.getLogger(NoOpTransactionHandler.class);

  /**
   * Accept all transactions.
   */
  public static final int ALL_OK = 1;
  /**
   * Reject all transactions.
   */
  public static final int ALL_INVALID_TRANSACTION = 2;
  /**
   * Issue an error for all transactions.
   */
  public static final int ALL_INTERNAL_ERROR = 3;
  private String familyName;
  private String version;
  private String namespace;
  private int strategy;

  /**
   * A transaction handler which does nothing with the data.
   * @param targetNs   the target namespace
   * @param targetVer  the target version
   * @param txStrategy the strategy to respond to transactions with
   */
  public NoOpTransactionHandler(final String targetNs, final String targetVer, final int txStrategy) {
    this.familyName = targetNs;
    this.version = targetVer;
    this.strategy = txStrategy;

    try {
      this.namespace = Utils.hash512(this.familyName.getBytes("UTF-8")).substring(0, NAMESPACE_LENGTH);
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException("UTF-8 encoding not supported on this platform!!", e);
    }
  }

  @Override
  public void apply(final TpProcessRequest transactionRequest, final Context state)
      throws InvalidTransactionException, InternalError {
    switch (this.strategy) {
    case ALL_OK:
      LOG.info(MessageFormat.format("Accepting transaction signature {0} at context {1}",
          transactionRequest.getSignature(), transactionRequest.getContextId()));
      return;
    case ALL_INVALID_TRANSACTION:
      LOG.info(MessageFormat.format("Rejecting(InvalidTransaction) transaction signature {0} at context {1}",
          transactionRequest.getSignature(), transactionRequest.getContextId()));
      throw new InvalidTransactionException("Throwing InvalidTransaction as configured");
    case ALL_INTERNAL_ERROR:
    default:
      LOG.info(MessageFormat.format("Rejecting(InternalError) transaction signature {0} at context {1}",
          transactionRequest.getSignature(), transactionRequest.getContextId()));
      throw new InternalError("Throwing InternalError as configured");
    }
  }

  @Override
  public Collection<String> getNameSpaces() {
    return Arrays.asList(new String[] {this.namespace});
  }

  @Override
  public String getVersion() {
    return this.version;
  }

  @Override
  public String transactionFamilyName() {
    return this.familyName;
  }

}
