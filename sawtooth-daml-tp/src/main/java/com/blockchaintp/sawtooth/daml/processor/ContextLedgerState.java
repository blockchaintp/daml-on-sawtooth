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

import static com.blockchaintp.sawtooth.timekeeper.Namespace.TIMEKEEPER_GLOBAL_RECORD;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import com.blockchaintp.sawtooth.daml.EventConstants;
import com.blockchaintp.sawtooth.daml.Namespace;
import com.blockchaintp.sawtooth.daml.protobuf.DamlTransaction;
import com.blockchaintp.sawtooth.daml.protobuf.DamlTransactionFragment;
import com.blockchaintp.sawtooth.daml.protobuf.VersionedEnvelope;
import com.blockchaintp.sawtooth.timekeeper.protobuf.TimeKeeperGlobalRecord;
import com.blockchaintp.utils.SawtoothClientUtils;
import com.daml.ledger.validator.LedgerStateOperations;
import com.daml.lf.data.Time.Timestamp;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.Timestamps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.logstash.logback.encoder.org.apache.commons.lang3.ArrayUtils;
import sawtooth.sdk.processor.Context;
import sawtooth.sdk.processor.exceptions.InternalError;
import sawtooth.sdk.processor.exceptions.InvalidTransactionException;
import scala.Function1;
import scala.Option;
import scala.Tuple2;
import scala.collection.JavaConverters;
import scala.collection.Seq;
import scala.concurrent.Future;
import scala.runtime.BoxedUnit;

/**
 * An implementation of LedgerState for DAML.
 *
 * @author scealiontach
 */
public final class ContextLedgerState implements LedgerState<String> {

  private static final int DEFAULT_MAX_VAL_SIZE = 128 * 1024;

  private static final Logger LOGGER = LoggerFactory.getLogger(ContextLedgerState.class.getName());

  /**
   * The state which this class wraps and delegates to.
   */
  private final Context state;

  /**
   * @param aState the State class which this object wraps.
   */
  public ContextLedgerState(final Context aState) {
    this.state = aState;
  }

  private ByteString getStateOrNull(final String address)
      throws InternalError, InvalidTransactionException {
    final Map<String, ByteString> stateMap = state.getState(List.of(address));
    if (stateMap.containsKey(address)) {
      final ByteString bs = stateMap.get(address);
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
  public ByteString getDamlState(final ByteString key)
      throws InternalError, InvalidTransactionException {
    String addr = Namespace.makeDamlStateAddress(key);
    LOGGER.debug("Reading address={}", addr);
    ByteString bs = getStateOrNull(addr);
    if (bs == null) {
      LOGGER.info("Read address={} is null", addr);
      return null;
    } else {
      try {
        List<VersionedEnvelope> veList = new ArrayList<>();
        VersionedEnvelope envelope = VersionedEnvelope.parseFrom(bs);
        veList.add(envelope);
        boolean hasMore = envelope.getHasMore();
        while (hasMore) {
          final String nextAddr = Namespace.makeAddress(Namespace.DAML_STATE_VALUE_NS, key.toStringUtf8(), "part",
              String.valueOf(veList.size() - 1));
          bs = getStateOrNull(nextAddr);
          if (bs == null) {
            hasMore = false;
          } else {
            envelope = VersionedEnvelope.parseFrom(bs);
            veList.add(envelope);
            hasMore = envelope.getHasMore();
          }
        }
        ByteString val = SawtoothClientUtils.unwrapMultipart(veList);
        LOGGER.info("Read address={} parts={} size={}", addr, veList.size(),
              val.size());
        return val;
      } catch (InvalidProtocolBufferException e) {
        throw new InvalidTransactionException(e.getMessage());
      }
    }
  }

  @Override
  public DamlTransaction assembleTransactionFragments(final DamlTransactionFragment endTx)
      throws InternalError, InvalidTransactionException {

    List<String> addrs = new ArrayList<>();
    for (int i = 0; i < endTx.getParts(); i++) {
      final String address = Namespace.makeAddress(Namespace.DAML_TX_NS, "fragment",
          endTx.getLogEntryId().toStringUtf8(), String.valueOf(endTx.getParts()),
          String.valueOf(i));
      addrs.add(address);
    }
    Map<String, ByteString> fragments = state.getState(addrs);
    ByteString result = null;
    byte[] accumulatedBytes = new byte[] {};
    try {
      int index = 0;
      for (String address : addrs) {
        ByteString fragBytes = fragments.get(address);
        if (fragBytes == null) {
          LOGGER.warn("Adding fragment index={} address={} is null", index, address);
        } else {
          LOGGER.warn("Adding fragment index={} address={} has size={}", index, address,
              fragBytes.size());
          DamlTransactionFragment frag = DamlTransactionFragment.parseFrom(fragBytes);
          accumulatedBytes =
              ArrayUtils.addAll(accumulatedBytes, frag.getSubmissionFragment().toByteArray());
        }
        index++;
      }
      result = ByteString.copyFrom(accumulatedBytes);
        DamlTransaction tx = DamlTransaction.parseFrom(result);
        return tx;
    } catch (InvalidProtocolBufferException e) {
      throw new InvalidTransactionException(e.getMessage());
    }
  }

  @Override
  public Map<ByteString, ByteString> getDamlStates(final Collection<ByteString> keys)
      throws InternalError, InvalidTransactionException {
    return getDamlStates(keys.toArray(new ByteString[] {}));
  }

  @Override
  public Map<ByteString, ByteString> getDamlStates(final ByteString... keys)
      throws InternalError, InvalidTransactionException {
    final Map<ByteString, ByteString> retMap = new HashMap<>();
    for (final ByteString k : keys) {
      final ByteString damlState = getDamlState(k);
      if (null != damlState) {
        retMap.put(k, damlState);
      } else {
        LOGGER.debug("Skipping key {} since value is null", k);
      }
    }
    return retMap;
  }

  @Override
  public void setDamlStates(final Collection<Entry<ByteString, ByteString>> entries)
      throws InternalError, InvalidTransactionException {
    final Map<String, ByteString> setMap = new HashMap<>();
    for (final Entry<ByteString, ByteString> e : entries) {
      final ByteString key = e.getKey();
      List<ByteString> parts =
          SawtoothClientUtils.wrapMultipart(e.getValue(), DEFAULT_MAX_VAL_SIZE);
      int index = 0;
      int size = 0;
      for (ByteString p : parts) {
        final String address;
        if (index == 0) {
          address = Namespace.makeDamlStateAddress(key);
        } else {
          address = Namespace.makeAddress(Namespace.DAML_STATE_VALUE_NS, key.toStringUtf8(), "part",
              String.valueOf(index));
        }
        setMap.put(address, p);
        index++;
        size += p.size();
      }
      LOGGER.info("Set address={} parts={} size={}", key.toStringUtf8(), index, size);
    }
    state.setState(setMap.entrySet());
  }

  @Override
  public void storeTransactionFragmet(final DamlTransactionFragment tx)
      throws InternalError, InvalidTransactionException {
    final String address = Namespace.makeAddress(Namespace.DAML_TX_NS, "fragment",
        tx.getLogEntryId().toStringUtf8(), String.valueOf(tx.getParts()),
        String.valueOf(tx.getPartNumber()));
    final ByteString val = tx.toByteString();
    LOGGER.info("Storing fragment at tx={} address={} size={}", tx.getLogEntryId().toStringUtf8(),
        address, val.size());
    final Map<String, ByteString> setMap = new HashMap<>();
    setMap.put(address, val);
    state.setState(setMap.entrySet());
  }


  @Override
  public void setDamlState(final ByteString key, final ByteString val)
      throws InternalError, InvalidTransactionException {
    final Map<ByteString, ByteString> setMap = new HashMap<>();
    setMap.put(key, val);
    setDamlStates(setMap.entrySet());
  }

  @Override
  public Timestamp getRecordTime() throws InternalError {
    try {
      LOGGER.debug("Fetching global time {}", TIMEKEEPER_GLOBAL_RECORD);
      final Map<String, ByteString> stateMap =
          state.getState(Arrays.asList(TIMEKEEPER_GLOBAL_RECORD));
      if (stateMap.containsKey(TIMEKEEPER_GLOBAL_RECORD)) {
        final TimeKeeperGlobalRecord tkgr =
            TimeKeeperGlobalRecord.parseFrom(stateMap.get(TIMEKEEPER_GLOBAL_RECORD));
        final com.google.protobuf.Timestamp timestamp = tkgr.getLastCalculatedTime();
        LOGGER.debug("Record Time = {}", tkgr.getLastCalculatedTime().toString());
        final long micros = Timestamps.toMicros(timestamp);
        return new Timestamp(micros);
      } else {
        return new Timestamp(1);
      }
    } catch (final InvalidTransactionException exc) {
      LOGGER.warn("Error fetching global time, assuming beginning of epoch {}", exc.getMessage());
      return new Timestamp(1);
    } catch (InternalError | InvalidProtocolBufferException exc) {
      final InternalError err = new InternalError(exc.getMessage());
      err.initCause(exc);
      throw err;
    }
  }

  @Override
  public Future<String> appendToLog(final ByteString key, final ByteString value) {
    try {
      final String logId = sendLogEvent(key, value);
      return Future.successful(logId);
    } catch (InternalError | InvalidTransactionException e) {
      LOGGER.error("Error sending log event");
      throw new RuntimeException(e);
    }
  }

  @Override
  public Future<Option<ByteString>> readState(final ByteString key) {
    try {
      final ByteString damlState = getDamlState(key);
      if (null == damlState) {
        return Future.successful(Option.empty());
      } else {
        return Future.successful(Option.apply(damlState));
      }
    } catch (InternalError | InvalidTransactionException e) {
      LOGGER.error("Error reading state");
      throw new RuntimeException(e);
    }
  }

  @Override
  public Future<Seq<Option<ByteString>>> readState(final Seq<ByteString> keys) {
    final Collection<ByteString> keyColl = JavaConverters.asJavaCollection(keys);
    final List<Option<ByteString>> retList = new ArrayList<>();
    try {
      final Map<ByteString, ByteString> damlStates = getDamlStates(keyColl);
      for (final ByteString k : keyColl) {
        if (damlStates.containsKey(k)) {
          retList.add(Option.apply(damlStates.get(k)));
        } else {
          retList.add(Option.empty());
        }
      }
      return Future.successful(JavaConverters.asScalaBuffer(retList));
    } catch (InternalError | InvalidTransactionException e) {
      LOGGER.error("Error reading state");
      throw new RuntimeException(e);
    }
  }

  Collection<Entry<ByteString, ByteString>> fromDamlSeqPair(
      final Seq<Tuple2<ByteString, ByteString>> seqOfPairs) {
    final Collection<Tuple2<ByteString, ByteString>> pairColl =
        JavaConverters.asJavaCollection(seqOfPairs);
    final List<Entry<ByteString, ByteString>> retList = new ArrayList<>();
    for (final Tuple2<ByteString, ByteString> t : pairColl) {
      retList.add(Map.entry(t._1(), t._2()));
    }
    return retList;
  }

  @Override
  public Future<BoxedUnit> writeState(final Seq<Tuple2<ByteString, ByteString>> keyValuePairs) {
    try {
      setDamlStates(fromDamlSeqPair(keyValuePairs));
      return Future.successful(BoxedUnit.UNIT);
    } catch (InternalError | InvalidTransactionException e) {
      LOGGER.error("Error writing state");
      throw new RuntimeException(e);
    }
  }

  @Override
  public Future<BoxedUnit> writeState(final ByteString key, final ByteString value) {
    try {
      setDamlState(key, value);
      return Future.successful(BoxedUnit.UNIT);
    } catch (InternalError | InvalidTransactionException e) {
      LOGGER.error("Error writing state");
      throw new RuntimeException(e);
    }
  }

  @Override
  public <T> Future<T> inTransaction(
      final Function1<LedgerStateOperations<String>, Future<T>> body) {
    return body.apply(this);
  }

  @Override
  public String sendLogEvent(final ByteString entryId, final ByteString entry)
      throws InternalError, InvalidTransactionException {
    List<ByteString> multipart = SawtoothClientUtils.wrapMultipart(entry, DEFAULT_MAX_VAL_SIZE);
    int part = 0;
    if (multipart.size() <= 1) {
      for (ByteString bs : multipart) {
        final Map<String, String> attrMap = new HashMap<>();
        attrMap.put(EventConstants.DAML_LOG_ENTRY_ID_EVENT_ATTRIBUTE, entryId.toStringUtf8());
        attrMap.put(EventConstants.DAML_LOG_ENTRY_ID_PART_COUNT_ATTRIBUTE,
            String.valueOf(multipart.size()));
        attrMap.put(EventConstants.DAML_LOG_ENTRY_ID_PART_ATTRIBUTE, String.valueOf(part));
        LOGGER.info("Sending event for {}, part={}, data size={}/{}", entryId.toStringUtf8(), part,
            bs.size(), entry.size());
        state.addEvent(EventConstants.DAML_LOG_EVENT_SUBJECT, attrMap.entrySet(), bs);
        part++;
      }
    } else {
      largeEvent(entryId.toStringUtf8(), multipart);
    }
    return entryId.toStringUtf8();
  }

  private void largeEvent(final String entryId, final List<ByteString> multipart)
      throws InternalError, InvalidTransactionException {
    LOGGER.info("Publishing large event for entry = {}", entryId);
    final Map<String, String> attrMap = new HashMap<>();
    attrMap.put(EventConstants.DAML_LOG_ENTRY_ID_EVENT_ATTRIBUTE, entryId);
    attrMap.put(EventConstants.DAML_LOG_ENTRY_ID_PART_COUNT_ATTRIBUTE,
        String.valueOf(multipart.size()));
    attrMap.put(EventConstants.DAML_LOG_ENTRY_FETCH_ATTRIBUTE, "true");

    int index = 0;
    Map<String, ByteString> setMap = new HashMap<>();
    StringBuilder fetchAddressBldr = new StringBuilder();
    int totalBytes = 0;
    for (ByteString bs : multipart) {
      String address = Namespace.makeAddress(Namespace.DAML_EVENT_NS, "logentry", entryId,
          "part", String.valueOf(index));
      if (index > 0) {
        fetchAddressBldr.append(",");
      }
      fetchAddressBldr.append(address);
      index++;
      totalBytes += bs.size();
      setMap.put(address, bs);
    }
    attrMap.put(EventConstants.DAML_LOG_FETCH_IDS_ATTRIBUTE, fetchAddressBldr.toString());
    state.setState(setMap.entrySet());
    LOGGER.info("Stored {} entries totalling {} bytes", multipart.size(), totalBytes);
    state.addEvent(EventConstants.DAML_LOG_EVENT_SUBJECT, attrMap.entrySet(), ByteString.EMPTY);
  }

}
