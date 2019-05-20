package com.blockchaintp.sawtooth.daml.processor.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.blockchaintp.sawtooth.daml.processor.LedgerState;
import com.blockchaintp.sawtooth.daml.util.EventConstants;
import com.blockchaintp.sawtooth.daml.util.Namespace;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntry;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntryId;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlStateKey;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlStateValue;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import sawtooth.sdk.processor.Context;
import sawtooth.sdk.processor.exceptions.InternalError;
import sawtooth.sdk.processor.exceptions.InvalidTransactionException;

/**
 * An implementation of LedgerState for DAML.
 * @author scealiontach
 */
public final class DamlLedgerState implements LedgerState {

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
  public DamlStateValue getDamlState(final DamlStateKey commandKey)
      throws InternalError, InvalidTransactionException {
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
        throw new InternalError(
            String.format("Content at address={} is not parsable as DamlCommandDedupValue", e.getKey()));
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
        throw new InternalError(String.format("Content at address={} is not parsable as DamlLogEntry", e.getKey()));
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

  @Override
  public void setDamlLogEntries(final Collection<Entry<DamlLogEntryId, DamlLogEntry>> entries)
      throws InternalError, InvalidTransactionException {
    Map<String, ByteString> setMap = new HashMap<>();
    for (Entry<DamlLogEntryId, DamlLogEntry> e : entries) {
      setMap.put(Namespace.makeDamlLogEntryAddress(e.getKey()), e.getValue().toByteString());
    }
    state.setState(setMap.entrySet());

  }

  @Override
  public void setDamlLogEntry(final DamlLogEntryId entryId, final DamlLogEntry entry)
      throws InternalError, InvalidTransactionException {
    Map<DamlLogEntryId, DamlLogEntry> setMap = new HashMap<>();
    setMap.put(entryId, entry);
    setDamlLogEntries(setMap.entrySet());
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
  public void sendLogEvent(final DamlLogEntryId entryId, final DamlLogEntry entry) throws InternalError {
    Map<String, String> attrMap = new HashMap<>();
    attrMap.put(EventConstants.DAML_LOG_ENTRY_ID_EVENT_ATTRIBUTE, entryId.getEntryId().toStringUtf8());
    state.addEvent(EventConstants.DAML_LOG_EVENT_SUBJECT, attrMap.entrySet(), entry.toByteString());

  }
}
