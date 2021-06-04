/* Copyright 2019-21 Blockchain Technology Partners
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

package com.blockchaintp.sawtooth.timekeeper.processor;

import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.blockchaintp.sawtooth.timekeeper.EventConstants;
import com.blockchaintp.sawtooth.timekeeper.Namespace;
import com.blockchaintp.sawtooth.timekeeper.exceptions.TimeKeeperException;
import com.blockchaintp.sawtooth.timekeeper.protobuf.TimeKeeperEvent;
import com.blockchaintp.sawtooth.timekeeper.protobuf.TimeKeeperGlobalRecord;
import com.blockchaintp.sawtooth.timekeeper.protobuf.TimeKeeperRecord;
import com.blockchaintp.sawtooth.timekeeper.protobuf.TimeKeeperUpdate;
import com.blockchaintp.utils.VersionedEnvelopeUtils;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sawtooth.sdk.processor.Context;
import sawtooth.sdk.processor.TransactionHandler;
import sawtooth.sdk.processor.exceptions.InternalError;
import sawtooth.sdk.processor.exceptions.InvalidTransactionException;
import sawtooth.sdk.protobuf.TpProcessRequest;
import sawtooth.sdk.protobuf.TransactionHeader;

/**
 * Accepts and validates TimeKeeperUpdate transactions and issues an event
 * containing the globally agreed time.
 */
public final class TimeKeeperTransactionHandler implements TransactionHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(TimeKeeperTransactionHandler.class);

  private final String familyName;
  private final String namespace;
  private final String version;

  /**
   * Default constructor.
   *
   */
  public TimeKeeperTransactionHandler() {
    this.familyName = Namespace.TIMEKEEPER_FAMILY_NAME;
    this.namespace = Namespace.getNameSpace();
    this.version = Namespace.TIMEKEEPER_FAMILY_VERSION_1_0;
  }

  @Override
  public String transactionFamilyName() {
    return this.familyName;
  }

  @Override
  public String getVersion() {
    return this.version;
  }

  @Override
  public Collection<String> getNameSpaces() {
    return Arrays.asList(this.namespace);
  }

  @Override
  public void apply(final TpProcessRequest txRequest, final Context state)
      throws InvalidTransactionException, InternalError {
    basicRequestChecks(txRequest);
    final String signerPublicKey = txRequest.getHeader().getSignerPublicKey();
    try {
      final ByteString unwrappedPayload = VersionedEnvelopeUtils.unwrap(txRequest.getPayload());
      final TimeKeeperUpdate update = TimeKeeperUpdate.parseFrom(unwrappedPayload);

      final String partRecordAddr = Namespace.makeAddress(this.namespace, signerPublicKey);
      LOGGER.debug("Getting global record state");
      final Map<String, ByteString> sourceData = state
          .getState(Arrays.asList(partRecordAddr, Namespace.TIMEKEEPER_GLOBAL_RECORD));

      ParticipantTimeState partTimeState;
      if (sourceData.containsKey(partRecordAddr)) {
        final TimeKeeperRecord myRecord = TimeKeeperRecord.parseFrom(sourceData.get(partRecordAddr));
        partTimeState = new ParticipantTimeState(myRecord);
        try {
          partTimeState.addUpdate(update);
        } catch (TimeKeeperException e) {
          throw new InvalidTransactionException(e.getMessage());
        }
      } else {
        partTimeState = new ParticipantTimeState(update);
      }
      final TimeKeeperRecord participantRecord = partTimeState.toTimeKeeperRecord();

      GlobalTimeState globalTimeState;
      if (sourceData.containsKey(Namespace.TIMEKEEPER_GLOBAL_RECORD)) {
        final TimeKeeperGlobalRecord globalRecord = TimeKeeperGlobalRecord
            .parseFrom(sourceData.get(Namespace.TIMEKEEPER_GLOBAL_RECORD));
        globalTimeState = new GlobalTimeState(globalRecord);
      } else {
        globalTimeState = new GlobalTimeState();
      }
      globalTimeState.addUpdate(ByteString.copyFromUtf8(signerPublicKey), update);
      final TimeKeeperGlobalRecord newGlobalRecord = globalTimeState.toTimeKeeperGlobalRecord();

      setTimeState(state, partRecordAddr, participantRecord, newGlobalRecord);

      sendTimeEvent(state, globalTimeState);
    } catch (InvalidProtocolBufferException exc) {
      final InvalidTransactionException ite = new InvalidTransactionException(
          "Transaction has bad format " + exc.getMessage());
      ite.initCause(exc);
      throw ite;
    }
  }

  private void setTimeState(final Context state, final String recordAddr, final TimeKeeperRecord tkRecord,
      final TimeKeeperGlobalRecord globalRecord) throws InternalError, InvalidTransactionException {
    final Map<String, ByteString> setMap = new HashMap<>();
    setMap.put(Namespace.TIMEKEEPER_GLOBAL_RECORD, globalRecord.toByteString());
    setMap.put(recordAddr, tkRecord.toByteString());
    state.setState(setMap.entrySet());
  }

  private void sendTimeEvent(final Context state, final GlobalTimeState globalTimeState) throws InternalError {
    final Timestamp currentGlobalTs = globalTimeState.getCurrentTime();
    final TimeKeeperEvent updateEventData = TimeKeeperEvent.newBuilder().setTimeUpdate(currentGlobalTs).build();

    final Map<String, String> attrMap = new HashMap<>();
    attrMap.put(EventConstants.TIMEKEEPER_MICROS_ATTRIBUTE, Long.toString(Timestamps.toMicros(currentGlobalTs)));
    LOGGER.debug("Global time now={}", new Date(Timestamps.toMillis(currentGlobalTs)));
    state.addEvent(EventConstants.TIMEKEEPER_EVENT_SUBJECT, attrMap.entrySet(), updateEventData.toByteString());
  }

  /**
   * Fundamental checks of the transaction.
   *
   * @param tpProcessRequest the process request
   * @throws InvalidTransactionException if the transaction fails because of a
   *                                     business rule validation error
   * @throws InternalError               if the transaction fails because of a
   *                                     system error
   */
  private void basicRequestChecks(final TpProcessRequest tpProcessRequest)
      throws InvalidTransactionException, InternalError {
    final TransactionHeader header = tpProcessRequest.getHeader();
    if (header == null) {
      throw new InvalidTransactionException("Header expected");
    }

    final ByteString payload = tpProcessRequest.getPayload();
    if (payload.size() == 0) {
      throw new InvalidTransactionException("Empty payload");
    }

    if (!header.getFamilyName().equals(this.familyName)) {
      throw new InvalidTransactionException("Family name does not match");
    }

    if (!header.getFamilyVersion().contentEquals(this.version)) {
      throw new InvalidTransactionException("Version does not match");
    }

  }

}
