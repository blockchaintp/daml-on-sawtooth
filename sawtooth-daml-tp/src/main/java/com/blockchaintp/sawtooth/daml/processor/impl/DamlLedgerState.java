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
 *
 * @author scealiontach
 */
public final class DamlLedgerState implements LedgerState {

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
        LOGGER.fine(String.format("Skipping key %s since value is null", k));
      }
    }
    return retMap;
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
        LOGGER.fine("Swapping DamlStateKey for DamlStateValue on COMMAND_DEDUP");
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
  public List<String> addDamlLogEntry(final DamlLogEntryId entryId, final DamlLogEntry entry)
      throws InternalError, InvalidTransactionException {
    Map<DamlLogEntryId, DamlLogEntry> setMap = new HashMap<>();
    setMap.put(entryId, entry);
    addDamlLogEntries(setMap.entrySet());
    sendLogEvent(entryId, entry);
    return new ArrayList<>();
  }

  private void sendLogEvent(final DamlLogEntryId entryId, final DamlLogEntry entry)
      throws InternalError, InvalidTransactionException {
    Map<String, String> attrMap = new HashMap<>();
    attrMap.put(EventConstants.DAML_LOG_ENTRY_ID_EVENT_ATTRIBUTE, entryId.getEntryId().toStringUtf8());
    attrMap.put(EventConstants.DAML_OFFSET_EVENT_ATTRIBUTE, Long.toString(1));
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

}
