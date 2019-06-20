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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
import com.blockchaintp.sawtooth.daml.protobuf.DamlLogEntryIndex;
import com.blockchaintp.sawtooth.daml.util.EventConstants;
import com.blockchaintp.sawtooth.daml.util.Namespace;
import com.blockchaintp.sawtooth.timekeeper.protobuf.TimeKeeperGlobalRecord;
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
 * @author scealiontach
 */
public final class DamlLedgerState implements LedgerState {

  private static final int COMPRESS_BUFFER_SIZE = 1024;

  private static final Logger LOGGER = Logger.getLogger(DamlLedgerState.class.getName());

  private static final int MAX_VALUE_SIZE = 64 * 1024 * 1024;

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

  private ByteString getMultipartState(final List<String> addrList) throws InternalError, InvalidTransactionException {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      int addressIdx = 0;
      for (String addr : addrList) {
        LOGGER.info(String.format("Get address %s", addr));
        Map<String, ByteString> addrMap = state.getState(List.of(addr));
        LOGGER.info(String.format("Get address %s size=", addr, addrMap.get(addr).size()));
        // All addresses are set or none are
        if (addressIdx == 0) {
          if (!addrMap.containsKey(addr) || addrMap.get(addr).equals(ByteString.EMPTY)) {
            return null;
          } else {
            addressIdx++;
          }
        } else {
          addressIdx++;
        }
        ByteString bs = addrMap.get(addr);
        baos.write(bs.toByteArray());
      }
      ByteString compressed = ByteString.copyFrom(baos.toByteArray());
      if (compressed.size() == 0) {
        return ByteString.EMPTY;
      } else {
        return uncompressByteString(compressed);
      }
    } catch (IOException exc) {
      throw new InternalError(exc.getMessage());
    }
  }

  @Override
  public DamlStateValue getDamlState(final DamlStateKey key) throws InternalError, InvalidTransactionException {
    String addr = Namespace.makeAddressForType(key);
    ByteString bs = getMultipartState(List.of(addr));
    if (bs == null) {
      return null;
    }
    if (key.getKeyCase().equals(DamlStateKey.KeyCase.COMMAND_DEDUP)) {
      return DamlStateValue.newBuilder().setCommandDedup(DamlCommandDedupValue.newBuilder().build()).build();
    }
    return KeyValueCommitting.unpackDamlStateValue(bs);
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
    ByteString bs = getMultipartState(List.of(addr));
    if (bs == null) {
      return null;
    }
    DamlLogEntry e = KeyValueCommitting.unpackDamlLogEntry(bs);
    return e;
  }

  private List<Entry<String, ByteString>> breakApartData(final List<String> leafAddresses, final ByteString data)
      throws InvalidTransactionException, InternalError {
    return this.breakApartData(leafAddresses, data, MAX_VALUE_SIZE);
  }

  private List<Entry<String, ByteString>> breakApartData(final List<String> leafAddresses, final ByteString data,
      final int leafSize) throws InvalidTransactionException, InternalError {
    List<Entry<String, ByteString>> retList = new ArrayList<>();
    if (data.size() > (leafAddresses.size() * leafSize)) {
      throw new InvalidTransactionException(
          String.format("Value is greater than max_value_size=%s, max_leaves=%s, leafSize=%s",
              leafAddresses.size() * leafSize, leafAddresses.size(), leafSize));
    }
    try (ByteArrayInputStream bais = new ByteArrayInputStream(data.toByteArray())) {
      for (String lAddr : leafAddresses) {
        byte[] readBuf;
        try {
          readBuf = bais.readNBytes(leafSize);
          Map<String, ByteString> setMap = new HashMap<>();
          setMap.put(lAddr, ByteString.copyFrom(readBuf));
          retList.addAll(setMap.entrySet());
        } catch (IOException exc) {
          throw new InternalError(exc.getMessage());
        }
      }
      return retList;
    } catch (IOException exc) {
      LOGGER.severe("ByteArrayInputStream.close() has thrown an IOException, this should never happen!");
      throw new InternalError(exc.getMessage());
    }
  }

  private void setMultipartState(final List<String> leafAddresses, final ByteString data)
      throws InvalidTransactionException, InternalError {
    ByteString compressedData = compressByteString(data);
    List<Entry<String, ByteString>> setList = breakApartData(leafAddresses, compressedData);
    for (Entry<String, ByteString> e : setList) {
      LOGGER.info(String.format("Set address=%s, size=%s", e.getKey(), e.getValue().size()));
      state.setState(List.of(e));
    }
  }

  @Override
  public void setDamlState(final DamlStateKey key, final DamlStateValue val)
      throws InternalError, InvalidTransactionException {
    ByteString packDamlStateValue;
    if (key.getKeyCase().equals(DamlStateKey.KeyCase.COMMAND_DEDUP)) {
      LOGGER.info("Swapping DamlStateKey for DamlStateValue on COMMAND_DEDUP");
      packDamlStateValue = KeyValueCommitting.packDamlStateKey(key);
    } else {
      packDamlStateValue = KeyValueCommitting.packDamlStateValue(val);
    }
    assert (packDamlStateValue.size() > 0);
    String leafAddresses = Namespace.makeAddressForType(key);
    setMultipartState(List.of(leafAddresses), packDamlStateValue);
  }

  private String[] addDamlLogEntries(final Collection<Entry<DamlLogEntryId, DamlLogEntry>> entries)
      throws InternalError, InvalidTransactionException {
    List<String> idList = new ArrayList<>();
    for (Entry<DamlLogEntryId, DamlLogEntry> e : entries) {
      String addr = Namespace.makeAddressForType(e.getKey());
      setMultipartState(List.of(addr), KeyValueCommitting.packDamlLogEntry(e.getValue()));
      idList.add(addr);
    }
    return idList.toArray(new String[] {});
  }

  @Override
  public void updateLogEntryIndex(final List<String> addresses) throws InternalError, InvalidTransactionException {
    DamlLogEntryIndex newIndex = DamlLogEntryIndex.newBuilder().clearAddresses().addAllAddresses(addresses).build();
    Map<String, ByteString> indexSetMap = new HashMap<>();
    indexSetMap.put(Namespace.DAML_LOG_ENTRY_LIST, compressByteString(newIndex.toByteString()));
    LOGGER.info("Fetching setting new log entry list");
    state.setState(indexSetMap.entrySet());
  }

  @Override
  public List<String> getLogEntryIndex() throws InternalError, InvalidTransactionException {
    LOGGER.info(String.format("Get LogEntryIndex address=%s", Namespace.DAML_LOG_ENTRY_LIST));
    Map<String, ByteString> stateMap = state.getState(List.of(Namespace.DAML_LOG_ENTRY_LIST));
    if (stateMap.containsKey(Namespace.DAML_LOG_ENTRY_LIST)) {
      ByteString compressed = stateMap.get(Namespace.DAML_LOG_ENTRY_LIST);
      LOGGER.info(String.format("Get LogEntryIndex address=%s,compressed=%s", Namespace.DAML_LOG_ENTRY_LIST,
          compressed.size()));
      ByteString data = uncompressByteString(compressed);
      LOGGER.info(String.format("Get LogEntryIndex address=%s,size=%s, compressed=%s", Namespace.DAML_LOG_ENTRY_LIST,
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
  public void setDamlStates(final Collection<Entry<DamlStateKey, DamlStateValue>> entries)
      throws InternalError, InvalidTransactionException {
    for (Entry<DamlStateKey, DamlStateValue> e : entries) {
      setDamlState(e.getKey(), e.getValue());
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
      LOGGER.info(String.format("Fetching global time %s", TIMEKEEPER_GLOBAL_RECORD));
      Map<String, ByteString> stateMap = state.getState(Arrays.asList(TIMEKEEPER_GLOBAL_RECORD));
      if (stateMap.containsKey(TIMEKEEPER_GLOBAL_RECORD)) {
        TimeKeeperGlobalRecord tkgr = TimeKeeperGlobalRecord.parseFrom(stateMap.get(TIMEKEEPER_GLOBAL_RECORD));
        LOGGER.info(String.format("Record Time = %s", tkgr.getLastCalculatedTime()));
        return tkgr.getLastCalculatedTime();
      } else {
        LOGGER.info("No global time has been set,assuming beginning of epoch");
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
      LOGGER.info(String.format("Compressed ByteString original_size=%s, new_size=%s", inputBytes.length, baos.size()));
      return bs;
    } catch (IOException exc) {
      LOGGER.severe("ByteArrayOutputStream.close() has thrown an error which should never happen!");
      throw new InternalError(exc.getMessage());
    }
  }

  private ByteString uncompressByteString(final ByteString compressedInput) throws InternalError {
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
        LOGGER.info(
            String.format("Uncompressed ByteString original_size=%s, new_size=%s", inputBytes.length, baos.size()));
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

}
