package com.blockchaintp.sawtooth.timekeeper.processor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;

import com.blockchaintp.sawtooth.timekeeper.protobuf.TimeKeeperEvent;
import com.blockchaintp.sawtooth.timekeeper.protobuf.TimeKeeperGlobalRecord;
import com.blockchaintp.sawtooth.timekeeper.protobuf.TimeKeeperParticipant;
import com.blockchaintp.sawtooth.timekeeper.protobuf.TimeKeeperRecord;
import com.blockchaintp.sawtooth.timekeeper.protobuf.TimeKeeperUpdate;
import com.blockchaintp.sawtooth.timekeeper.util.EventConstants;
import com.blockchaintp.sawtooth.timekeeper.util.Namespace;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;

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

  private static final Logger LOGGER = Logger.getLogger(TimeKeeperTransactionHandler.class.getName());

  private static final int DEFAULT_MAX_HISTORY_SIZE = 10;

  private final String familyName;
  private final String namespace;
  private final String version;

  /**
   * Default constructor.
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
  public void apply(final TpProcessRequest transactionRequest, final Context state)
      throws InvalidTransactionException, InternalError {
    basicRequestChecks(transactionRequest);
    String signerPublicKey = transactionRequest.getHeader().getSignerPublicKey();
    try {
      TimeKeeperUpdate update = TimeKeeperUpdate.parseFrom(transactionRequest.getPayload());
      String myRecordAddr = Namespace.makeAddress(this.namespace, signerPublicKey);
      LOGGER.warning("Getting global record state");
      Map<String, ByteString> sourceData = state
          .getState(Arrays.asList(myRecordAddr, Namespace.TIMEKEEPER_GLOBAL_RECORD));

      TimeKeeperRecord myRecord;
      if (sourceData.containsKey(myRecordAddr)) {
        myRecord = TimeKeeperRecord.parseFrom(sourceData.get(myRecordAddr));
      } else {
        myRecord = TimeKeeperRecord.getDefaultInstance();
      }
      LOGGER.warning(String.format("My last time was %s", myRecord.getLastCalculatedTime()));

      TimeKeeperGlobalRecord globalRecord;
      if (sourceData.containsKey(Namespace.TIMEKEEPER_GLOBAL_RECORD)) {
        globalRecord = TimeKeeperGlobalRecord.parseFrom(sourceData.get(Namespace.TIMEKEEPER_GLOBAL_RECORD));
      } else {
        globalRecord = TimeKeeperGlobalRecord.getDefaultInstance();
      }
      LOGGER.warning(String.format("Global time is %s", globalRecord.getLastCalculatedTime()));

      Map<String, ByteString> setMap = new HashMap<String, ByteString>();
      List<Timestamp> timeHistoryList = new ArrayList<>(myRecord.getTimeHistoryList());
      timeHistoryList.add(update.getTimeUpdate());
      timeHistoryList = prune(timeHistoryList);

      // Set new time to max of new time and last reported time
      Timestamp newCalculatedTs = getMaxTs(update.getTimeUpdate(), myRecord.getLastCalculatedTime());
      LOGGER.warning(String.format("My new time is %s", newCalculatedTs));

      TimeKeeperRecord.Builder newRecordBldr = TimeKeeperRecord.newBuilder(myRecord);
      newRecordBldr.clearTimeHistory().addAllTimeHistory(timeHistoryList).setLastCalculatedTime(newCalculatedTs);
      TimeKeeperRecord newRecord = newRecordBldr.build();
      LOGGER.warning("writing participant record");
      setMap.put(myRecordAddr, newRecord.toByteString());

      TimeKeeperParticipant myNewParticipant = TimeKeeperParticipant.newBuilder().setLastCalculatedTime(newCalculatedTs)
          .setParticipantPublicKey(ByteString.copyFromUtf8(signerPublicKey)).build();
      List<TimeKeeperParticipant> participantList = globalRecord.getParticipantList();
      List<TimeKeeperParticipant> newParticipantList = new ArrayList<>();
      List<Timestamp> participantTimes = new ArrayList<>();
      boolean newParticipant = true;
      for (TimeKeeperParticipant p : participantList) {
        TimeKeeperParticipant addend = p;
        if (p.getParticipantPublicKey().equals(myNewParticipant.getParticipantPublicKey())) {
          addend = myNewParticipant;
          newParticipant = false;
        } else {
          addend = p;
        }
        newParticipantList.add(addend);
        participantTimes.add(addend.getLastCalculatedTime());
      }
      if (newParticipant) {
        newParticipantList.add(myNewParticipant);
        participantTimes.add(myNewParticipant.getLastCalculatedTime());
      }
      Timestamp globalAverageTs = getAverageTimeStamp(participantTimes);
      Timestamp newGlobalTs = getMaxTs(globalAverageTs, globalRecord.getLastCalculatedTime());

      TimeKeeperGlobalRecord.Builder newGlobalRecordBldr = TimeKeeperGlobalRecord.newBuilder(globalRecord);
      newGlobalRecordBldr.setLastCalculatedTime(newGlobalTs).clearParticipant().addAllParticipant(newParticipantList);
      TimeKeeperGlobalRecord newGlobalRecord = newGlobalRecordBldr.build();
      setMap.put(Namespace.TIMEKEEPER_GLOBAL_RECORD, newGlobalRecord.toByteString());

      LOGGER.warning("writing global record");
      state.setState(setMap.entrySet());

      TimeKeeperEvent updateEventData = TimeKeeperEvent.newBuilder().setTimeUpdate(newGlobalTs).build();
      Map<String, String> attrMap = new HashMap<>();
      attrMap.put(EventConstants.TIMEKEEPER_MICROS_ATTRIBUTE, Long.toString(Timestamps.toMicros(newGlobalTs)));
      LOGGER.log(Level.WARNING, String.format("New time event %s", newGlobalTs));
      state.addEvent(EventConstants.TIMEKEEPER_EVENT_SUBJECT, attrMap.entrySet(), updateEventData.toByteString());
    } catch (InvalidProtocolBufferException exc) {
      throw new InvalidTransactionException("Transaction has bad format " + exc.getMessage());
    }
  }

  private List<Timestamp> prune(final List<Timestamp> timeHistoryList) {
    // TODO Put together more policies for pruning history
    List<Timestamp> newHistoryList;
    if (timeHistoryList.size() > DEFAULT_MAX_HISTORY_SIZE) {
      // len=5, 0-4, Max=3 234 start=len-max+1
      int lastIndex = timeHistoryList.size() - 1;
      int startIndex = lastIndex - DEFAULT_MAX_HISTORY_SIZE + 1;
      newHistoryList = timeHistoryList.subList(startIndex, lastIndex);
    } else {
      newHistoryList = timeHistoryList;
    }
    return newHistoryList;
  }

  private Timestamp getMaxTs(final Timestamp... timestamps) {
    Timestamp maxTs = null;
    for (Timestamp ts : timestamps) {
      if (maxTs == null) {
        maxTs = ts;
      } else {
        int cmp = Timestamps.compare(maxTs, ts);
        if (cmp < 0) {
          maxTs = ts;
        }
      }
    }
    return maxTs;
  }

  private Timestamp getAverageTimeStamp(final List<Timestamp> tsList) {
    long totalMicros = 0;
    for (Timestamp ts : tsList) {
      totalMicros = Math.addExact(totalMicros, Timestamps.toMicros(ts));
    }
    long aveMicros = Math.floorDiv(totalMicros, tsList.size());
    return Timestamps.fromMicros(aveMicros);
  }

  /**
   * Fundamental checks of the transaction.
   * @param tpProcessRequest the process request
   * @throws InvalidTransactionException if the transaction fails because of a
   *                                     business rule validation error
   * @throws InternalError               if the transaction fails because of a
   *                                     system error
   */
  private void basicRequestChecks(final TpProcessRequest tpProcessRequest)
      throws InvalidTransactionException, InternalError {
    TransactionHeader header = tpProcessRequest.getHeader();
    if (header == null) {
      throw new InvalidTransactionException("Header expected");
    }

    ByteString payload = tpProcessRequest.getPayload();
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
