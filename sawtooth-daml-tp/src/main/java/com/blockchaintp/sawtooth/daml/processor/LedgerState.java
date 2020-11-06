/*
 * Copyright 2020 Blockchain Technology Partners
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.blockchaintp.sawtooth.daml.processor;

import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import com.blockchaintp.sawtooth.daml.protobuf.DamlTransactionFragment;
import com.blockchaintp.sawtooth.daml.protobuf.DamlTransaction;
import com.daml.ledger.validator.LedgerStateAccess;
import com.daml.ledger.validator.LedgerStateOperations;
import com.google.protobuf.ByteString;
import sawtooth.sdk.processor.exceptions.InternalError;
import sawtooth.sdk.processor.exceptions.InvalidTransactionException;

/**
 * An interface to keep the coupling to the context implementation loose.
 *
 * @param <T> the type of the log identifier
 * @author scealiontach
 */
public interface LedgerState<T> extends LedgerStateOperations<T>, LedgerStateAccess<T> {
  /**
   * Fetch a single DamlStateValue from the ledger state.
   *
   * @param key key identifying the operation
   * @return the value for this key or null
   * @throws InvalidTransactionException when there is an error relating to the client input
   * @throws InternalError               when there is an unexpected back end error.
   */
  ByteString getDamlState(ByteString key) throws InternalError, InvalidTransactionException;

  /**
   * Fetch a collection of DamlStateValues from the ledger state.
   *
   * @param keys a collection keys identifying the object
   * @return a map of keys to values
   * @throws InvalidTransactionException when there is an error relating to the client input
   * @throws InternalError               when there is an unexpected back end error.
   */
  Map<ByteString, ByteString> getDamlStates(Collection<ByteString> keys)
      throws InternalError, InvalidTransactionException;

  /**
   * Fetch a collection of DamlStateValues from the ledger state.
   *
   * @param keys one or more keys identifying the values to be fetches
   * @return a map of keys to values
   * @throws InvalidTransactionException when there is an error relating to the client input
   * @throws InternalError               when there is an unexpected back end error.
   */
  Map<ByteString, ByteString> getDamlStates(ByteString... keys)
      throws InternalError, InvalidTransactionException;

  /**
   * @param key The key identifying this value
   * @param val the value
   * @throws InvalidTransactionException when there is an error relating to the client input
   * @throws InternalError               when there is an unexpected back end error.
   */
  void setDamlState(ByteString key, ByteString val)
      throws InternalError, InvalidTransactionException;

  /**
   * Store a collection of values at the logical keys provided.
   *
   * @param entries a collection of tuples of DamlStateKey to DamlStateValue mappings
   * @throws InvalidTransactionException when there is an error relating to the client input
   * @throws InternalError               when there is an unexpected back end error.
   */
  void setDamlStates(Collection<Entry<ByteString, ByteString>> entries)
      throws InternalError, InvalidTransactionException;

  /**
   * Record an event containing the provided log info.
   *
   * @param entryId the id of this log entry
   * @param entry   the entry itself
   * @return the identifier of the log
   * @throws InternalError               when there is an unexpected back end error
   * @throws InvalidTransactionException when the data itself is invalid
   */
  T sendLogEvent(ByteString entryId, ByteString entry)
      throws InternalError, InvalidTransactionException;

  /**
   * Fetch the current global record time.
   *
   * @return a Timestamp
   * @throws InternalError when there is an unexpected back end error.
   */
  com.daml.lf.data.Time.Timestamp getRecordTime() throws InternalError;

  /**
   * Stores the transaction fragment in state.
   *
   * @param tx the transaction fragment to store
   * @throws InternalError               when there is an unexpected back end error.
   * @throws InvalidTransactionException when the data itself is invalid
   */
  void storeTransactionFragmet(DamlTransactionFragment tx)
      throws InternalError, InvalidTransactionException;

  /**
   * Assembles the transactiong fragments matching the end transactions into a complete transaction.
   *
   * @param endTx the final transaction fragment in the sequence, which is empty
   * @return the assembled transaction
   * @throws InternalError               when there is an unexpected back end error.
   * @throws InvalidTransactionException when the data itself is invalid
   */
  DamlTransaction assembleTransactionFragments(DamlTransactionFragment endTx)
      throws InternalError, InvalidTransactionException;

  /**
   * Flush any deferred events, for instance to put them after state updates.
   * @throws InternalError
   */
  void flushDeferredEvents() throws InternalError;
}
