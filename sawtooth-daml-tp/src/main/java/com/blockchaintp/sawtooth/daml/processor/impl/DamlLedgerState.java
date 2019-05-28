package com.blockchaintp.sawtooth.daml.processor.impl;

import static com.blockchaintp.sawtooth.timekeeper.util.Namespace.TIMEKEEPER_GLOBAL_RECORD;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;

import com.blockchaintp.sawtooth.daml.processor.LedgerState;
import com.blockchaintp.sawtooth.daml.protobuf.DamlLogEntryIndex;
import com.blockchaintp.sawtooth.daml.util.EventConstants;
import com.blockchaintp.sawtooth.daml.util.Namespace;
import com.blockchaintp.sawtooth.timekeeper.protobuf.TimeKeeperGlobalRecord;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntry;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntryId;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlStateKey;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlStateValue;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Timestamp;

import sawtooth.sdk.processor.Context;
import sawtooth.sdk.processor.exceptions.InternalError;
import sawtooth.sdk.processor.exceptions.InvalidTransactionException;

/**
 * An implementation of LedgerState for DAML.
 * @author scealiontach
 */
public final class DamlLedgerState implements LedgerState {

  private static final Logger LOGGER = Logger.getLogger(DamlLedgerState.class.getName());

  /**
   * The state which this class wraps and delegates to.
   */
  private Context state;

  /**
   * @param aState the State class which this object wraps.
   */
  public DamlLedgerState(final Context aState) {
    this.state = aState;
  }

  @Override
  public DamlStateValue getDamlState(final DamlStateKey commandKey) throws InternalError, InvalidTransactionException {
    return getDamlStates(commandKey).get(commandKey);
  }

  @Override
  public Map<DamlStateKey, DamlStateValue> getDamlStates(final Collection<DamlStateKey> keys)
      throws InternalError, InvalidTransactionException {
    return getDamlStates(keys.toArray(new DamlStateKey[] {}));
  }

  @Override
  public Map<DamlStateKey, DamlStateValue> getDamlStates(final DamlStateKey... keys)
      throws InternalError, InvalidTransactionException {
    List<String> addresses = new ArrayList<>();
    Map<String, DamlStateKey> addressToKey = new HashMap<>();
    for (DamlStateKey k : keys) {
      String address = Namespace.makeDamlStateAddress(k);
      addresses.add(address);
      addressToKey.put(address, k);
    }
    Map<String, ByteString> stateMap = state.getState(addresses);
    Map<DamlStateKey, DamlStateValue> retMap = new HashMap<>();
    for (Map.Entry<String, ByteString> e : stateMap.entrySet()) {
      DamlStateKey k = addressToKey.get(e.getKey());
      try {
        DamlStateValue v = DamlStateValue.parseFrom(e.getValue());
        retMap.put(k, v);
      } catch (InvalidProtocolBufferException exc) {
        InternalError ie = new InternalError(
            String.format("Content at address={} is not parsable as DamlStateValue", e.getKey()));
        ie.initCause(exc);
        throw ie;
      }
    }
    return retMap;
  }

  @Override
  public Map<DamlLogEntryId, DamlLogEntry> getDamlLogEntries(final Collection<DamlLogEntryId> keys)
      throws InternalError, InvalidTransactionException {
    return getDamlLogEntries(keys.toArray(new DamlLogEntryId[] {}));
  }

  @Override
  public Map<DamlLogEntryId, DamlLogEntry> getDamlLogEntries(final DamlLogEntryId... keys)
      throws InternalError, InvalidTransactionException {
    List<String> addresses = new ArrayList<>();
    Map<String, DamlLogEntryId> addressToKey = new HashMap<>();
    for (DamlLogEntryId k : keys) {
      String address = Namespace.makeDamlLogEntryAddress(k);
      addresses.add(address);
      addressToKey.put(address, k);
    }
    Map<String, ByteString> stateMap = state.getState(addresses);
    Map<DamlLogEntryId, DamlLogEntry> retMap = new HashMap<>();
    for (Map.Entry<String, ByteString> e : stateMap.entrySet()) {
      DamlLogEntryId k = addressToKey.get(e.getKey());
      try {
        DamlLogEntry v = DamlLogEntry.parseFrom(e.getValue());
        retMap.put(k, v);
      } catch (InvalidProtocolBufferException exc) {
        InternalError ie = new InternalError(
            String.format("Content at address={} is not parsable as DamlLogEntry", e.getKey()));
        ie.initCause(exc);
        throw ie;
      }
    }
    return retMap;
  }

  @Override
  public DamlLogEntry getDamlLogEntry(final DamlLogEntryId entryId) throws InternalError, InvalidTransactionException {
    return getDamlLogEntries(entryId).get(entryId);
  }

  @Override
  public void setDamlState(final DamlStateKey key, final DamlStateValue val)
      throws InternalError, InvalidTransactionException {
    Map<String, ByteString> setMap = new HashMap<>();
    setMap.put(Namespace.makeDamlStateAddress(key), val.toByteString());
    state.setState(setMap.entrySet());
  }

  private String[] addDamlLogEntries(final Collection<Entry<DamlLogEntryId, DamlLogEntry>> entries)
      throws InternalError, InvalidTransactionException {
    Map<String, ByteString> setMap = new HashMap<>();
    List<String> idList = new ArrayList<>();
    for (Entry<DamlLogEntryId, DamlLogEntry> e : entries) {
      String address = Namespace.makeDamlLogEntryAddress(e.getKey());
      setMap.put(address, e.getValue().toByteString());
      idList.add(address);
    }
    state.setState(setMap.entrySet());
    return idList.toArray(new String[] {});
  }

  @Override
  public void addDamlLogEntry(final DamlLogEntryId entryId, final DamlLogEntry entry)
      throws InternalError, InvalidTransactionException {
    Map<DamlLogEntryId, DamlLogEntry> setMap = new HashMap<>();
    setMap.put(entryId, entry);
    String[] addresses = addDamlLogEntries(setMap.entrySet());
    List<String> oldAddresses;
    try {
      Map<String, ByteString> damlLogEntryList = state.getState(Arrays.asList(Namespace.DAML_LOG_ENTRY_LIST));
      if (damlLogEntryList.containsKey(Namespace.DAML_LOG_ENTRY_LIST)) {
        ByteString data = damlLogEntryList.get(Namespace.DAML_LOG_ENTRY_LIST);
        try {
          DamlLogEntryIndex index = DamlLogEntryIndex.parseFrom(data);
          oldAddresses = index.getAddressesList();
        } catch (InvalidProtocolBufferException exc) {
          InternalError err = new InternalError(exc.getMessage());
          err.initCause(exc);
          throw err;
        }
      } else {
        // entry list has never been set, initialize
        oldAddresses = new ArrayList<>();
      }
    } catch (InvalidTransactionException exc) {
      // entry list has never been set, initialize
      oldAddresses = new ArrayList<>();
    }
    List<String> newIndexAddresses = new ArrayList<>();
    newIndexAddresses.addAll(oldAddresses);
    newIndexAddresses.addAll(Arrays.asList(addresses));
    DamlLogEntryIndex newIndex = DamlLogEntryIndex.newBuilder().clearAddresses().addAllAddresses(newIndexAddresses)
        .build();
    Map<String, ByteString> indexSetMap = new HashMap<>();
    indexSetMap.put(Namespace.DAML_LOG_ENTRY_LIST, newIndex.toByteString());
    state.setState(indexSetMap.entrySet());
    sendLogEvent(entryId, entry, newIndexAddresses.size());
  }

  @Override
  public void setDamlStates(final Collection<Entry<DamlStateKey, DamlStateValue>> entries)
      throws InternalError, InvalidTransactionException {
    Map<String, ByteString> setMap = new HashMap<>();
    for (Entry<DamlStateKey, DamlStateValue> e : entries) {
      setMap.put(Namespace.makeDamlStateAddress(e.getKey()), e.getValue().toByteString());
    }
    state.setState(setMap.entrySet());
  }

  @Override
  public void sendLogEvent(final DamlLogEntryId entryId, final DamlLogEntry entry, final long offset)
      throws InternalError {
    Map<String, String> attrMap = new HashMap<>();
    attrMap.put(EventConstants.DAML_LOG_ENTRY_ID_EVENT_ATTRIBUTE, entryId.getEntryId().toStringUtf8());
    attrMap.put(EventConstants.DAML_OFFSET_EVENT_ATTRIBUTE, Long.toString(offset));
    state.addEvent(EventConstants.DAML_LOG_EVENT_SUBJECT, attrMap.entrySet(), entry.toByteString());
  }

  @Override
  public Timestamp getRecordTime() throws InternalError {
    try {
      Map<String, ByteString> stateMap = state.getState(Arrays.asList(TIMEKEEPER_GLOBAL_RECORD));
      if (stateMap.containsKey(TIMEKEEPER_GLOBAL_RECORD)) {
        TimeKeeperGlobalRecord tkgr = TimeKeeperGlobalRecord.parseFrom(stateMap.get(TIMEKEEPER_GLOBAL_RECORD));
        return tkgr.getLastCalculatedTime();
      } else {
        LOGGER.warning("No global time was retrieved,assuming beginning of epoch");
        return Timestamp.newBuilder().setSeconds(0).setNanos(0).build();
      }
    } catch (InvalidTransactionException exc) {
      LOGGER.warning("No global time was retrieved,assuming beginning of epoch");
      return Timestamp.newBuilder().setSeconds(0).setNanos(0).build();
    } catch (InternalError | InvalidProtocolBufferException exc) {
      InternalError err = new InternalError(exc.getMessage());
      err.initCause(exc);
      throw err;
    }
  }
}
