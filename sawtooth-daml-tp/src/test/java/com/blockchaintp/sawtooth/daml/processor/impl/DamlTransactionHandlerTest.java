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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.javatuples.Pair;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.blockchaintp.sawtooth.daml.processor.DamlCommitter;
import com.blockchaintp.sawtooth.daml.protobuf.DamlLogEntryIndex;
import com.blockchaintp.sawtooth.daml.util.Namespace;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntry;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlPackageUploadEntry;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlStateKey;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlStateValue;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlSubmission;
import com.digitalasset.daml_lf.DamlLf.Archive;
import com.google.protobuf.ByteString;

import net.bytebuddy.utility.RandomString;
import sawtooth.sdk.processor.Context;
import sawtooth.sdk.processor.exceptions.InternalError;
import sawtooth.sdk.processor.exceptions.InvalidTransactionException;
import sawtooth.sdk.protobuf.TpProcessRequest;
import sawtooth.sdk.protobuf.TransactionHeader;
import scala.Tuple2;

public class DamlTransactionHandlerTest {

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
  public void testGetVersion() {
    DamlCommitter committer = mock(DamlCommitter.class);
    DamlTransactionHandler handler = new DamlTransactionHandler(committer);
    final String result = handler.getVersion();
    assertNotNull("getVersion returning null", result);
    assertTrue(String.format("getVersion  Expected: %s Got %s", Namespace.DAML_FAMILY_VERSION_1_0, result),
        result.equals(Namespace.DAML_FAMILY_VERSION_1_0));
  }

  @Test
  public void testGetNameSpaces() {
    DamlCommitter committer = mock(DamlCommitter.class);
    DamlTransactionHandler handler = new DamlTransactionHandler(committer);
    Collection<String> result = handler.getNameSpaces();
    assertNotNull("getNamespace returning null", handler.getNameSpaces());
    assertTrue("getNamespace returning one element", (result.size() == 1));

    String expectedNamespaceHash;
    expectedNamespaceHash = Namespace.getNameSpace();
    String actualNamespaceHash = result.iterator().next();
    assertTrue(String.format("getNamespace Expect: %s Got: %s", expectedNamespaceHash, actualNamespaceHash),
        (actualNamespaceHash.equals(expectedNamespaceHash)));
  }

  @Test
  public void testTransactionFamilyName() {
    DamlCommitter committer = mock(DamlCommitter.class);
    DamlTransactionHandler handler = new DamlTransactionHandler(committer);
    final String result = handler.transactionFamilyName();
    assertNotNull("transactionFamilyName returning null", result);
    assertTrue(String.format("transactionFamilyName Expected: %s Got %s", Namespace.DAML_FAMILY_NAME, result),
        result.equals(Namespace.DAML_FAMILY_NAME));

  }

  @Test
  public void testApplyEmptyPayload() {
    Pair<Context, Map<String, ByteString>> p = getMockState();
    Context state = p.getValue0();
    DamlCommitter committer = mock(DamlCommitter.class);
    DamlTransactionHandler handler = new DamlTransactionHandler(committer);
    TpProcessRequest transactionRequest = TpProcessRequest.getDefaultInstance().toBuilder().clearPayload().build();

    try {
      handler.apply(transactionRequest, state);
      fail("Invalid payload should throw InvalidTransaction");
    } catch (InvalidTransactionException e) {
      // Exception called and consumed
    } catch (InternalError e) {
      fail("Invalid payload should throw InvalidTransaction");
    }

  }

  @Test
  public void testApplyRequestInvalidFamilyName() {
    DamlCommitter committer = mock(DamlCommitter.class);
    DamlTransactionHandler handler = new DamlTransactionHandler(committer);
    TpProcessRequest transactionRequest = mock(TpProcessRequest.class);
    Pair<Context, Map<String, ByteString>> p = getMockState();
    Context state = p.getValue0();

    ByteString payload = mock(ByteString.class);
    when(transactionRequest.getPayload()).thenReturn(payload);

    TransactionHeader txnHeader = mock(TransactionHeader.class);
    when(transactionRequest.getHeader()).thenReturn(txnHeader);
    when(txnHeader.getFamilyName()).thenReturn("help");
    when(txnHeader.getFamilyVersion()).thenReturn("help");

    try {
      handler.apply(transactionRequest, state);
      fail("Familyname and/or version do not match");
    } catch (InvalidTransactionException e) {
      // Expected to be called
    } catch (InternalError e) {
      fail("Familyname and/or version do not match");
    }

  }

  @Test
  public void testApply() {
    DamlCommitter committer = mock(DamlCommitter.class);
    DamlTransactionHandler handler = new DamlTransactionHandler(committer);
    TransactionHeader txHeader = TransactionHeader.newBuilder().setFamilyName(Namespace.DAML_FAMILY_NAME)
        .setFamilyVersion(Namespace.DAML_FAMILY_VERSION_1_0).addInputs(Namespace.DAML_LOG_ENTRY_LIST)
        .addInputs(com.blockchaintp.sawtooth.timekeeper.util.Namespace.TIMEKEEPER_GLOBAL_RECORD).build();
    Archive archive = Archive.getDefaultInstance();
    DamlStateKey archiveKey = DamlStateKey.newBuilder().setPackageId(RandomString.make(RANDOM_STRING_LENGTH)).build();
    DamlSubmission submission = DamlSubmission.newBuilder()
        .setPackageUploadEntry(DamlPackageUploadEntry.newBuilder().addArchives(Archive.getDefaultInstance()).build())
        .build();
    TpProcessRequest transactionRequest = TpProcessRequest.newBuilder().setHeader(txHeader)
        .setPayload(submission.toByteString()).build();
    Pair<Context, Map<String, ByteString>> p = getMockState();
    Context state = p.getValue0();

    Map<DamlStateKey, DamlStateValue> stateMap = new HashMap<>();
    DamlStateValue archiveValue = DamlStateValue.newBuilder().setArchive(archive).build();
    stateMap.put(archiveKey, archiveValue);
    when(committer.processSubmission(any(), any(), any(), any(), any(), any()))
        .thenReturn(Tuple2.apply(DamlLogEntry.getDefaultInstance(), stateMap));
    try {
      handler.apply(transactionRequest, state);
    } catch (InvalidTransactionException | InternalError exc) {
      exc.printStackTrace();
      fail("No exceptions should be thrown");
    }
  }

}
