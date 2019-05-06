package com.blockchaintp.sawtooth.daml.processor.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.blockchaintp.sawtooth.daml.processor.LedgerState;
import com.blockchaintp.sawtooth.daml.util.Namespace;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlCommandDedupKey;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlCommandDedupValue;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlContractId;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlContractState;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntry;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntryId;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlStateKey;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlStateValue;
import com.digitalasset.daml_lf.DamlLf.Archive;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import sawtooth.sdk.processor.State;
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
  private State state;

  /**
   * @param aState the State class which this object wraps.
   */
  public DamlLedgerState(final State aState) {
    this.state = aState;
  }

  @Override
  public DamlCommandDedupValue getDamlCommandDedup(final DamlCommandDedupKey commandKey)
      throws InternalError, InvalidTransactionException {
    return getDamlCommandDedups(commandKey).get(commandKey);
  }

  @Override
  public Map<DamlStateKey, DamlStateValue> getDamlCommandDedups(final Collection<DamlStateKey> keys)
      throws InternalError, InvalidTransactionException {
    return getDamlCommandDedups(keys.toArray(new DamlStateKey[] {}));
  }

  @Override
  public Map<DamlCommandDedupKey, DamlCommandDedupValue> getDamlCommandDedups(final DamlCommandDedupKey... keys)
      throws InternalError, InvalidTransactionException {
    List<String> addresses = new ArrayList<>();
    Map<String, DamlCommandDedupKey> addressToKey = new HashMap<>();
    for (DamlCommandDedupKey k : keys) {
      String address = Namespace.makeDamlCommadDedupAddress(k);
      addresses.add(address);
      addressToKey.put(address, k);
    }
    Map<String, ByteString> stateMap = state.getState(addresses);
    Map<DamlCommandDedupKey, DamlCommandDedupValue> retMap = new HashMap<>();
    for (Map.Entry<String, ByteString> e : stateMap.entrySet()) {
      DamlCommandDedupKey k = addressToKey.get(e.getKey());
      try {
        DamlCommandDedupValue v = DamlCommandDedupValue.parseFrom(e.getValue());
        retMap.put(k, v);
      } catch (InvalidProtocolBufferException exc) {
        throw new InternalError(
            String.format("Content at address={} is not parsable as DamlCommandDedupValue", e.getKey()));
      }
    }
    return retMap;
  }

  @Override
  public Map<DamlStateKey, DamlStateValue> getDamlCommandDedups(final DamlStateKey... keys)
      throws InternalError, InvalidTransactionException {
    Map<DamlCommandDedupKey, DamlStateKey> subToKey = new HashMap<>();
    for (DamlStateKey k : keys) {
      subToKey.put(k.getCommandDedup(), k);
    }
    Map<DamlCommandDedupKey, DamlCommandDedupValue> subEntries = getDamlCommandDedups(
        subToKey.keySet().toArray(new DamlCommandDedupKey[] {}));
    Map<DamlStateKey, DamlStateValue> returnMap = new HashMap<>();
    for (DamlCommandDedupKey k : subEntries.keySet()) {
      DamlStateValue val = DamlStateValue.newBuilder().setCommandDedup(subEntries.get(k)).build();
      returnMap.put(subToKey.get(k), val);
    }
    return returnMap;
  }

  @Override
  public DamlContractState getDamlContract(final DamlContractId key) throws InternalError, InvalidTransactionException {
    return getDamlContracts(key).get(key);
  }

  @Override
  public Map<DamlStateKey, DamlStateValue> getDamlContracts(final Collection<DamlStateKey> keys)
      throws InternalError, InvalidTransactionException {
    return getDamlContracts(keys.toArray(new DamlStateKey[] {}));
  }

  @Override
  public Map<DamlContractId, DamlContractState> getDamlContracts(final DamlContractId... keys)
      throws InternalError, InvalidTransactionException {
    List<String> addresses = new ArrayList<>();
    Map<String, DamlContractId> addressToKey = new HashMap<>();
    for (DamlContractId k : keys) {
      String address = Namespace.makeDamlContractAddress(k);
      addresses.add(address);
      addressToKey.put(address, k);
    }
    Map<String, ByteString> stateMap = state.getState(addresses);
    Map<DamlContractId, DamlContractState> retMap = new HashMap<>();
    for (Map.Entry<String, ByteString> e : stateMap.entrySet()) {
      DamlContractId k = addressToKey.get(e.getKey());
      try {
        DamlContractState v = DamlContractState.parseFrom(e.getValue());
        retMap.put(k, v);
      } catch (InvalidProtocolBufferException exc) {
        throw new InternalError(
            String.format("Content at address={} is not parsable as DamlContractState", e.getKey()));
      }
    }
    return retMap;
  }

  @Override
  public Map<DamlStateKey, DamlStateValue> getDamlContracts(final DamlStateKey... keys)
      throws InternalError, InvalidTransactionException {
    Map<DamlContractId, DamlStateKey> subToKey = new HashMap<>();
    for (DamlStateKey k : keys) {
      subToKey.put(k.getContractId(), k);
    }
    Map<DamlContractId, DamlContractState> subEntries = getDamlContracts(
        subToKey.keySet().toArray(new DamlContractId[] {}));
    Map<DamlStateKey, DamlStateValue> returnMap = new HashMap<>();
    for (DamlContractId k : subEntries.keySet()) {
      DamlStateValue val = DamlStateValue.newBuilder().setContractState(subEntries.get(k)).build();
      returnMap.put(subToKey.get(k), val);
    }
    return returnMap;
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
  public Archive getDamlPackage(final String key) throws InternalError, InvalidTransactionException {
    return getDamlPackages(key).get(key);
  }

  @Override
  public Map<DamlStateKey, DamlStateValue> getDamlPackages(final Collection<DamlStateKey> keys)
      throws InternalError, InvalidTransactionException {
    return getDamlPackages(keys.toArray(new DamlStateKey[] {}));
  }

  @Override
  public Map<DamlStateKey, DamlStateValue> getDamlPackages(final DamlStateKey... keys)
      throws InternalError, InvalidTransactionException {
    Map<String, DamlStateKey> subToKey = new HashMap<>();
    for (DamlStateKey k : keys) {
      subToKey.put(k.getPackageId(), k);
    }
    Map<String, Archive> subEntries = getDamlPackages(subToKey.keySet().toArray(new String[] {}));
    Map<DamlStateKey, DamlStateValue> returnMap = new HashMap<>();
    for (String k : subEntries.keySet()) {
      DamlStateValue val = DamlStateValue.newBuilder().setArchive(subEntries.get(k)).build();
      returnMap.put(subToKey.get(k), val);
    }
    return returnMap;
  }

  @Override
  public Map<String, Archive> getDamlPackages(final String... keys) throws InternalError, InvalidTransactionException {
    List<String> addresses = new ArrayList<>();
    Map<String, String> addressToKey = new HashMap<>();
    for (String k : keys) {
      String address = Namespace.makeDamlPackageAddress(k);
      addresses.add(address);
      addressToKey.put(address, k);
    }
    Map<String, ByteString> stateMap = state.getState(addresses);
    Map<String, Archive> retMap = new HashMap<>();
    for (Map.Entry<String, ByteString> e : stateMap.entrySet()) {
      String k = addressToKey.get(e.getKey());
      try {
        Archive v = Archive.parseFrom(e.getValue());
        retMap.put(k, v);
      } catch (InvalidProtocolBufferException exc) {
        throw new InternalError(String.format("Content at address={} is not parsable as Archive", e.getKey()));
      }
    }
    return retMap;
  }

  @Override
  public void setDamlCommandDedup(final DamlCommandDedupKey key, final DamlCommandDedupValue val)
      throws InternalError, InvalidTransactionException {
    Map<String, ByteString> setMap = new HashMap<>();
    setMap.put(Namespace.makeDamlCommadDedupAddress(key), val.toByteString());
    state.setState(setMap.entrySet());
  }

  @Override
  public void setDamlContract(final DamlContractId key, final DamlContractState val)
      throws InternalError, InvalidTransactionException {
    Map<String, ByteString> setMap = new HashMap<>();
    setMap.put(Namespace.makeDamlContractAddress(key), val.toByteString());
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
  public void setDamlPackage(final String packageId, final Archive val)
      throws InternalError, InvalidTransactionException {
    Map<String, Archive> setMap = new HashMap<>();
    setMap.put(packageId, val);
    setDamlPackages(setMap.entrySet());
  }

  @Override
  public void setDamlPackages(final Collection<Entry<String, Archive>> entries)
      throws InternalError, InvalidTransactionException {
    Map<String, ByteString> setMap = new HashMap<>();
    for (Entry<String, Archive> e : entries) {
      setMap.put(Namespace.makeDamlPackageAddress(e.getKey()), e.getValue().toByteString());
    }
    state.setState(setMap.entrySet());
  }
}
