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
package com.blockchaintp.sawtooth.daml.processor.impl;

import static com.blockchaintp.sawtooth.timekeeper.util.Namespace.TIMEKEEPER_GLOBAL_RECORD;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import com.blockchaintp.sawtooth.daml.processor.LedgerState;
import com.blockchaintp.sawtooth.daml.protobuf.ConfigurationEntry;
import com.blockchaintp.sawtooth.daml.protobuf.ConfigurationMap;
import com.blockchaintp.sawtooth.daml.protobuf.DamlLogEntryIndex;
import com.blockchaintp.sawtooth.daml.protobuf.SawtoothDamlParty;
import com.blockchaintp.sawtooth.daml.util.EventConstants;
import com.blockchaintp.sawtooth.daml.util.Namespace;
import com.blockchaintp.sawtooth.timekeeper.protobuf.TimeKeeperGlobalRecord;
import com.daml.ledger.participant.state.backport.TimeModel;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlCommandDedupValue;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntry;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntryId;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlStateKey;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlStateValue;
import com.daml.ledger.participant.state.kvutils.KeyValueCommitting;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Timestamp;

import sawtooth.sdk.processor.Context;
import sawtooth.sdk.processor.exceptions.InternalError;
import sawtooth.sdk.processor.exceptions.InvalidTransactionException;

/**
 * An implementation of LedgerState for DAML.
 *
 * @author scealiontach
 */
public final class DamlLedgerState implements LedgerState {

  private static final String TIMEMODEL_CONFIG = "com.blockchaintp.sawtooth.daml.timemodel";

  private static final String MAX_TTL_KEY = TIMEMODEL_CONFIG + ".maxTtl";

  private static final String MAX_CLOCK_SKEW_KEY = TIMEMODEL_CONFIG + ".maxClockSkew";

  private static final String MIN_TRANSACTION_LATENCY_KEY = TIMEMODEL_CONFIG + ".minTransactionLatency";

  private static final int COMPRESS_BUFFER_SIZE = 1024;

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

  private ByteString getStateOrNull(final String address) throws InternalError, InvalidTransactionException {
    Map<String, ByteString> stateMap = state.getState(List.of(address));
    if (stateMap.containsKey(address)) {
      ByteString bs = stateMap.get(address);
      if (bs.isEmpty() || bs == null) {
        return null;
      } else {
        return bs;
      }
    } else {
      return null;
    }
  }

  @Override
  public DamlStateValue getDamlState(final DamlStateKey key) throws InternalError, InvalidTransactionException {
    String addr = Namespace.makeAddressForType(key);
    ByteString bs = getStateOrNull(addr);
    if (bs == null) {
      return null;
    } else {
      if (key.getKeyCase().equals(DamlStateKey.KeyCase.COMMAND_DEDUP)) {
        return DamlStateValue.newBuilder().setCommandDedup(DamlCommandDedupValue.newBuilder().build()).build();
      }
      return KeyValueCommitting.unpackDamlStateValue(uncompressByteString(bs));

    }
  }

  @Override
  public Map<DamlStateKey, DamlStateValue> getDamlStates(final Collection<DamlStateKey> keys)
      throws InternalError, InvalidTransactionException {
    return getDamlStates(keys.toArray(new DamlStateKey[] {}));
  }

  @Override
  public Map<DamlStateKey, DamlStateValue> getDamlStates(final DamlStateKey... keys)
      throws InternalError, InvalidTransactionException {
    Map<DamlStateKey, DamlStateValue> retMap = new HashMap<>();
    for (DamlStateKey k : keys) {
      DamlStateValue damlState = getDamlState(k);
      if (null != damlState) {
        retMap.put(k, damlState);
      } else {
        LOGGER.warning(String.format("Skipping key %s since value is null", k));
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
    Map<DamlLogEntryId, DamlLogEntry> retMap = new HashMap<>();
    for (DamlLogEntryId k : keys) {
      DamlLogEntry e = getDamlLogEntry(k);
      if (e != null) {
        retMap.put(k, e);
      }
    }
    return retMap;
  }

  @Override
  public DamlLogEntry getDamlLogEntry(final DamlLogEntryId entryId) throws InternalError, InvalidTransactionException {
    String addr = Namespace.makeAddressForType(entryId);
    ByteString bs = getStateOrNull(addr);
    if (bs == null) {
      return null;
    }
    DamlLogEntry e = KeyValueCommitting.unpackDamlLogEntry(uncompressByteString(bs));
    return e;
  }

  @Override
  public void setDamlStates(final Collection<Entry<DamlStateKey, DamlStateValue>> entries)
      throws InternalError, InvalidTransactionException {
    Map<String, ByteString> setMap = new HashMap<>();
    for (Entry<DamlStateKey, DamlStateValue> e : entries) {
      DamlStateKey key = e.getKey();
      DamlStateValue val = e.getValue();
      ByteString packDamlStateValue;
      if (key.getKeyCase().equals(DamlStateKey.KeyCase.COMMAND_DEDUP)) {
        LOGGER.info("Swapping DamlStateKey for DamlStateValue on COMMAND_DEDUP");
        packDamlStateValue = KeyValueCommitting.packDamlStateKey(key);
      } else {
        packDamlStateValue = KeyValueCommitting.packDamlStateValue(val);
      }
      assert (packDamlStateValue.size() > 0);
      String address = Namespace.makeAddressForType(key);
      setMap.put(address, compressByteString(packDamlStateValue));
    }
    state.setState(setMap.entrySet());
  }

  @Override
  public void setDamlState(final DamlStateKey key, final DamlStateValue val)
      throws InternalError, InvalidTransactionException {
    Map<DamlStateKey, DamlStateValue> setMap = new HashMap<>();
    setMap.put(key, val);
    setDamlStates(setMap.entrySet());
  }

  private String[] addDamlLogEntries(final Collection<Entry<DamlLogEntryId, DamlLogEntry>> entries)
      throws InternalError, InvalidTransactionException {
    List<String> idList = new ArrayList<>();
    Map<String, ByteString> setMap = new HashMap<>();
    for (Entry<DamlLogEntryId, DamlLogEntry> e : entries) {
      String addr = Namespace.makeAddressForType(e.getKey());
      setMap.put(addr, compressByteString(KeyValueCommitting.packDamlLogEntry(e.getValue())));
      idList.add(addr);
    }
    state.setState(setMap.entrySet());
    return idList.toArray(new String[] {});
  }

  @Override
  public List<String> addDamlLogEntry(final DamlLogEntryId entryId, final DamlLogEntry entry,
      final List<String> currentEntryList) throws InternalError, InvalidTransactionException {
    Map<DamlLogEntryId, DamlLogEntry> setMap = new HashMap<>();
    setMap.put(entryId, entry);
    String[] addresses = addDamlLogEntries(setMap.entrySet());
    List<String> retList = new ArrayList<>(currentEntryList);
    retList.addAll(Arrays.asList(addresses));
    sendLogEvent(entryId, entry, retList.size());
    return retList;
  }

  @Override
  public void updateLogEntryIndex(final List<String> addresses) throws InternalError, InvalidTransactionException {
    DamlLogEntryIndex newIndex = DamlLogEntryIndex.newBuilder().clearAddresses().addAllAddresses(addresses).build();
    Map<String, ByteString> indexSetMap = new HashMap<>();
    indexSetMap.put(Namespace.DAML_LOG_ENTRY_LIST, compressByteString(newIndex.toByteString()));
    LOGGER.fine("Setting new log entry list");
    state.setState(indexSetMap.entrySet());
  }

  @Override
  public List<String> getLogEntryIndex() throws InternalError, InvalidTransactionException {
    LOGGER.fine(String.format("Get LogEntryIndex address=%s", Namespace.DAML_LOG_ENTRY_LIST));
    Map<String, ByteString> stateMap = state.getState(List.of(Namespace.DAML_LOG_ENTRY_LIST));
    if (stateMap.containsKey(Namespace.DAML_LOG_ENTRY_LIST)) {
      ByteString compressed = stateMap.get(Namespace.DAML_LOG_ENTRY_LIST);
      LOGGER.fine(String.format("Get LogEntryIndex address=%s,compressed=%s", Namespace.DAML_LOG_ENTRY_LIST,
          compressed.size()));
      ByteString data = uncompressByteString(compressed);
      LOGGER.fine(String.format("Get LogEntryIndex address=%s,size=%s, compressed=%s", Namespace.DAML_LOG_ENTRY_LIST,
          data.size(), compressed.size()));
      try {
        DamlLogEntryIndex dlei = DamlLogEntryIndex.parseFrom(data);
        return dlei.getAddressesList();
      } catch (InvalidProtocolBufferException exc) {
        throw new InternalError(exc.getMessage());
      }
    } else {
      return new ArrayList<String>();
    }
  }

  @Override
  public void sendLogEvent(final DamlLogEntryId entryId, final DamlLogEntry entry, final long offset)
      throws InternalError, InvalidTransactionException {
    Map<String, String> attrMap = new HashMap<>();
    attrMap.put(EventConstants.DAML_LOG_ENTRY_ID_EVENT_ATTRIBUTE, entryId.getEntryId().toStringUtf8());
    attrMap.put(EventConstants.DAML_OFFSET_EVENT_ATTRIBUTE, Long.toString(offset));
    ByteString packedData = KeyValueCommitting.packDamlLogEntry(entry);
    ByteString compressedData = compressByteString(packedData);
    LOGGER.info(String.format("Sending event for %s, size=%s, compressed=%s", entryId.getEntryId().toStringUtf8(),
        packedData.size(), compressedData.size()));
    state.addEvent(EventConstants.DAML_LOG_EVENT_SUBJECT, attrMap.entrySet(), compressedData);
  }

  @Override
  public Timestamp getRecordTime() throws InternalError {
    try {
      LOGGER.fine(String.format("Fetching global time %s", TIMEKEEPER_GLOBAL_RECORD));
      Map<String, ByteString> stateMap = state.getState(Arrays.asList(TIMEKEEPER_GLOBAL_RECORD));
      if (stateMap.containsKey(TIMEKEEPER_GLOBAL_RECORD)) {
        TimeKeeperGlobalRecord tkgr = TimeKeeperGlobalRecord.parseFrom(stateMap.get(TIMEKEEPER_GLOBAL_RECORD));
        LOGGER.fine(String.format("Record Time = %s", tkgr.getLastCalculatedTime()));
        return tkgr.getLastCalculatedTime();
      } else {
        LOGGER.warning("No global time has been set,assuming beginning of epoch");
        return Timestamp.newBuilder().setSeconds(0).setNanos(0).build();
      }
    } catch (InvalidTransactionException exc) {
      LOGGER.warning(String.format("Error fetching global time, assuming beginning of epoch %s", exc.getMessage()));
      return Timestamp.newBuilder().setSeconds(0).setNanos(0).build();
    } catch (InternalError | InvalidProtocolBufferException exc) {
      InternalError err = new InternalError(exc.getMessage());
      err.initCause(exc);
      throw err;
    }
  }

  private ByteString compressByteString(final ByteString input) throws InternalError {
    long compressStart = System.currentTimeMillis();
    if (input.size() == 0) {
      return ByteString.EMPTY;
    }
    Deflater deflater = new Deflater();
    deflater.setLevel(Deflater.BEST_SPEED);
    byte[] inputBytes = input.toByteArray();

    deflater.setInput(inputBytes);
    deflater.finish();

    try (ByteArrayOutputStream baos = new ByteArrayOutputStream(inputBytes.length);) {
      byte[] buffer = new byte[COMPRESS_BUFFER_SIZE];
      while (!deflater.finished()) {
        int bCount = deflater.deflate(buffer);
        baos.write(buffer, 0, bCount);
      }
      deflater.end();

      ByteString bs = ByteString.copyFrom(baos.toByteArray());
      long compressStop = System.currentTimeMillis();
      long compressTime = compressStop - compressStart;
      LOGGER.fine(String.format("Compressed ByteString time=%s, original_size=%s, new_size=%s", compressTime,
          inputBytes.length, baos.size()));
      return bs;
    } catch (IOException exc) {
      LOGGER.severe("ByteArrayOutputStream.close() has thrown an error which should never happen!");
      throw new InternalError(exc.getMessage());
    }
  }

  private ByteString uncompressByteString(final ByteString compressedInput) throws InternalError {
    long uncompressStart = System.currentTimeMillis();
    if (compressedInput.size() == 0) {
      return ByteString.EMPTY;
    }
    Inflater inflater = new Inflater();
    byte[] inputBytes = compressedInput.toByteArray();
    inflater.setInput(inputBytes);

    try (ByteArrayOutputStream baos = new ByteArrayOutputStream(inputBytes.length)) {
      byte[] buffer = new byte[COMPRESS_BUFFER_SIZE];
      try {
        while (!inflater.finished()) {
          int bCount = inflater.inflate(buffer);
          baos.write(buffer, 0, bCount);
        }
        inflater.end();

        ByteString bs = ByteString.copyFrom(baos.toByteArray());
        long uncompressStop = System.currentTimeMillis();
        long uncompressTime = uncompressStop - uncompressStart;
        LOGGER.fine(String.format("Uncompressed ByteString time=%s, original_size=%s, new_size=%s", uncompressTime,
            inputBytes.length, baos.size()));
        return bs;
      } catch (DataFormatException exc) {
        LOGGER.severe(String.format("Error uncompressing stream, throwing InternalError! %s", exc.getMessage()));
        throw new InternalError(exc.getMessage());
      }
    } catch (IOException exc) {
      LOGGER.severe("ByteArrayOutputStream.close() has thrown an error which should never happen!");
      throw new InternalError(exc.getMessage());
    }
  }

  @Override
  public TimeModel getTimeModel() throws InternalError, InvalidTransactionException {
    ConfigurationMap configMap;
    ByteString bs;
    Map<String, ByteString> configEntry = state.getState(List.of(Namespace.DAML_CONFIG_TIME_MODEL));
    bs = configEntry.getOrDefault(Namespace.DAML_CONFIG_TIME_MODEL, ByteString.EMPTY);
    try {
      configMap = ConfigurationMap.parseFrom(bs);
      Duration maxTtl = null;
      Duration maxClockSkew = null;
      Duration minTxLatency = null;

      for (ConfigurationEntry ce : configMap.getEntriesList()) {
        if (ce.getKey().equals(MIN_TRANSACTION_LATENCY_KEY)) {
          minTxLatency = Duration.parse(ce.getValue().toStringUtf8());
        }
        if (ce.getKey().equals(MAX_CLOCK_SKEW_KEY)) {
          maxClockSkew = Duration.parse(ce.getValue().toStringUtf8());
        }
        if (ce.getKey().equals(MAX_TTL_KEY)) {
          maxTtl = Duration.parse(ce.getValue().toStringUtf8());
        }
      }
      if (null == maxTtl || null == maxClockSkew || null == minTxLatency) {
        return null;
      } else {
        return new TimeModel(minTxLatency, maxClockSkew, maxTtl);
      }
    } catch (InvalidProtocolBufferException exc) {
      InternalError internalError = new InternalError(exc.getMessage());
      internalError.initCause(exc);
      throw internalError;
    }
  }

  @Override
  public void setTimeModel(final TimeModel tm) throws InternalError, InvalidTransactionException {
    Map<String, ByteString> configEntry = state.getState(List.of(Namespace.DAML_CONFIG_TIME_MODEL));
    List<ConfigurationEntry> newCEList = new ArrayList<>();
    try {
      ConfigurationMap configMap = ConfigurationMap
          .parseFrom(configEntry.getOrDefault(Namespace.DAML_CONFIG_TIME_MODEL, ByteString.EMPTY));
      for (ConfigurationEntry ce : configMap.getEntriesList()) {
        if (!ce.getKey().startsWith(TIMEMODEL_CONFIG)) {
          newCEList.add(ce);
        }
      }
      ByteString minTxLatency = ByteString.copyFromUtf8(tm.minTransactionLatency().toString());
      ConfigurationEntry minTxLatencyCE = ConfigurationEntry.newBuilder().setKey(MIN_TRANSACTION_LATENCY_KEY)
          .setValue(minTxLatency).build();
      newCEList.add(minTxLatencyCE);

      ByteString maxClockSkew = ByteString.copyFromUtf8(tm.maxClockSkew().toString());
      ConfigurationEntry maxClockSkewCE = ConfigurationEntry.newBuilder().setKey(MAX_CLOCK_SKEW_KEY)
          .setValue(maxClockSkew).build();
      newCEList.add(maxClockSkewCE);

      ByteString maxTtl = ByteString.copyFromUtf8(tm.maxTtl().toString());
      ConfigurationEntry maxTtlCE = ConfigurationEntry.newBuilder().setKey(MAX_TTL_KEY).setValue(maxTtl).build();
      newCEList.add(maxTtlCE);

      ConfigurationMap newMap = ConfigurationMap.newBuilder().addAllEntries(newCEList).build();

      configEntry.put(Namespace.DAML_CONFIG_TIME_MODEL, newMap.toByteString());
      state.setState(configEntry.entrySet());
    } catch (InvalidProtocolBufferException exc) {
      InternalError internalError = new InternalError(exc.getMessage());
      internalError.initCause(exc);
      throw internalError;
    }
  }

  @Override
  public SawtoothDamlParty getParty(String partyId) throws InternalError, InvalidTransactionException {
    String address = Namespace.makeDamlPartyAddress(partyId);
    Map<String, ByteString> smap = state.getState(List.of(address));
    if (smap.containsKey(address)) {
      ByteString bs = smap.get(address);
      if (null == bs || bs.isEmpty()) {
        return null;
      } else {
        try {
          return SawtoothDamlParty.parseFrom(bs);
        } catch (InvalidProtocolBufferException e) {
          throw new InternalError(e.getMessage());
        }
      }
    } else {
      return null;
    }
  }

  @Override
  public SawtoothDamlParty setParty(SawtoothDamlParty partyEntry) throws InternalError, InvalidTransactionException {
    ByteString bs = partyEntry.toByteString();
    String address = Namespace.makeAddressForType(partyEntry);
    state.setState(List.of(Map.entry(address, bs)));
    return partyEntry;
  }

  @Override
  public void addParty(SawtoothDamlParty partyEntry) throws InternalError, InvalidTransactionException {
    SawtoothDamlParty previousParty = getParty(partyEntry.getHint());
    if (null == previousParty) {
      setParty(partyEntry);
    } else {
      throw new InvalidTransactionException(
          String.format("DAML Party identified by %s already exists", partyEntry.getHint()));
    }
  }

}
