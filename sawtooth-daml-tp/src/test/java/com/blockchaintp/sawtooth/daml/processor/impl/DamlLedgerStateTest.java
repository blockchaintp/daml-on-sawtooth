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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.javatuples.Pair;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.blockchaintp.sawtooth.daml.processor.LedgerState;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlCommandDedupKey;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlCommandDedupValue;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntry;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntryId;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlStateKey;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlStateValue;
import com.google.protobuf.ByteString;

import net.bytebuddy.utility.RandomString;
import sawtooth.sdk.processor.Context;
import sawtooth.sdk.processor.exceptions.InternalError;
import sawtooth.sdk.processor.exceptions.InvalidTransactionException;

public class DamlLedgerStateTest {

  private static final int RANDOM_STRING_LENGTH = 10;

  private Pair<Context, Map<String, ByteString>> getMockState() {
    Map<String, ByteString> stateMap = new HashMap<>();
    Context s = mock(Context.class);
    try {
      when(s.getState(anyCollection())).thenAnswer(new Answer<Map<String, ByteString>>() {
        @Override
        public Map<String, ByteString> answer(final InvocationOnMock invocation) throws Throwable {
          Collection<String> addresses = invocation.getArgument(0);
          Map<String, ByteString> results = new HashMap<>();
          for (String a : addresses) {
            ByteString value = stateMap.get(a);
            if (null != value) {
              results.put(a, value);
            }
          }
          if (results.size() != addresses.size()) {
            throw new InvalidTransactionException("Returned fewer results than expected");
          }
          return results;
        }
      });
      when(s.setState(anyCollection())).then(new Answer<Collection<String>>() {

        @Override
        public Collection<String> answer(final InvocationOnMock invocation) throws Throwable {
          Collection<Entry<String, ByteString>> entries = invocation.getArgument(0);
          ArrayList<String> retList = new ArrayList<>();
          for (Entry<String, ByteString> e : entries) {
            stateMap.put(e.getKey(), e.getValue());
            retList.add(e.getKey());
          }
          return retList;
        }

      });
    } catch (InternalError | InvalidTransactionException e) {
      throw new RuntimeException("Shouldn't ever get an exception when building the mock");
    }
    return Pair.with(s, stateMap);
  }

  @Test
  public void testGetSetDamlState() {
    Pair<Context, Map<String, ByteString>> mockPair = getMockState();
    Context mockState = mockPair.getValue0();
    LedgerState ledgerState = new DamlLedgerState(mockState);
    String appId = RandomString.make(RANDOM_STRING_LENGTH);
    String submitter = RandomString.make(RANDOM_STRING_LENGTH);
    String commandId = RandomString.make(RANDOM_STRING_LENGTH);
    DamlCommandDedupKey firstDedupKey = DamlCommandDedupKey.newBuilder().setApplicationId(appId).setSubmitter(submitter)
        .setCommandId(commandId).build();
    DamlStateKey firstKey = DamlStateKey.newBuilder().setCommandDedup(firstDedupKey).build();
    DamlCommandDedupKey emptyDedupKey = DamlCommandDedupKey.newBuilder().setApplicationId(appId).setSubmitter(submitter)
        .setCommandId(RandomString.make(RANDOM_STRING_LENGTH)).build();
    DamlStateKey emptyKey = DamlStateKey.newBuilder().setCommandDedup(emptyDedupKey).build();
    DamlCommandDedupValue firstDedupVal = DamlCommandDedupValue.newBuilder().build();
    DamlStateValue firstVal = DamlStateValue.newBuilder().setCommandDedup(firstDedupVal).build();
    try {
      ledgerState.setDamlState(firstKey, firstVal);
      DamlStateValue damlCommandDedup = ledgerState.getDamlState(firstKey);
      assertTrue(String.format("%s != %s", firstVal,damlCommandDedup),firstVal.equals(damlCommandDedup));

      DamlStateKey stateKey = DamlStateKey.newBuilder().setCommandDedup(firstDedupKey).build();
      Map<DamlStateKey, DamlStateValue> retMap = ledgerState.getDamlStates(Arrays.asList(stateKey));
      assertTrue(firstVal.equals(retMap.get(stateKey)));
      try {
        ledgerState.getDamlState(emptyKey);
      } catch (InvalidTransactionException exc) {
        // Expected
      } catch (InternalError exc) {
        fail(String.format("Should not have issued an {}", exc.getClass().getName()));
      }
    } catch (InternalError | InvalidTransactionException exc) {
      fail("No exceptions should be thrown");
    }
  }

  @Test
  public void testGetSetDamlLogEntries() {
    Pair<Context, Map<String, ByteString>> mockPair = getMockState();
    Context mockState = mockPair.getValue0();
    LedgerState ledgerState = new DamlLedgerState(mockState);
    DamlLogEntryId firstKey = DamlLogEntryId.newBuilder()
        .setEntryId(ByteString.copyFromUtf8(RandomString.make(RANDOM_STRING_LENGTH))).build();
    DamlLogEntryId emptyKey = DamlLogEntryId.newBuilder()
        .setEntryId(ByteString.copyFromUtf8(RandomString.make(RANDOM_STRING_LENGTH))).build();
    DamlLogEntry firstVal = DamlLogEntry.getDefaultInstance();
    try {
      ledgerState.addDamlLogEntry(firstKey, firstVal, new ArrayList<String>());
      DamlLogEntry testVal = ledgerState.getDamlLogEntry(firstKey);
      assertTrue(firstVal.equals(testVal));
      // DamlStateKey stateKey = DamlStateKey.newBuilder().set
      Map<DamlLogEntryId, DamlLogEntry> retMap = ledgerState.getDamlLogEntries(Arrays.asList(firstKey));
      assertTrue(firstVal.equals(retMap.get(firstKey)));
      try {
        ledgerState.getDamlLogEntry(emptyKey);
      } catch (InvalidTransactionException exc) {
        // Expected
      } catch (InternalError exc) {
        fail(String.format("Should not have issued an {}", exc.getClass().getName()));
      }
    } catch (InternalError | InvalidTransactionException exc) {
      fail("No exceptions should be thrown");
    }
  }

}
