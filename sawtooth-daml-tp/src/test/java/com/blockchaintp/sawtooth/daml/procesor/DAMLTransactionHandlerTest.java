package com.blockchaintp.sawtooth.daml.procesor;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
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

import com.blockchaintp.sawtooth.daml.processor.Namespace;
import com.blockchaintp.sawtooth.daml.processor.impl.DAMLTransactionHandler;
import com.google.protobuf.ByteString;

import sawtooth.sdk.processor.State;
import sawtooth.sdk.processor.exceptions.InternalError;
import sawtooth.sdk.processor.exceptions.InvalidTransactionException;
import sawtooth.sdk.protobuf.TpProcessRequest;
import sawtooth.sdk.protobuf.TransactionHeader;

public class DAMLTransactionHandlerTest {

	private Pair<State, Map<String, ByteString>> getMockState() {
		Map<String, ByteString> stateMap = new HashMap<>();
		State s = mock(State.class);
		try {
			when(s.getState(anyCollection())).thenAnswer(new Answer<Map<String, ByteString>>() {
				@Override
				public Map<String, ByteString> answer(InvocationOnMock invocation) throws Throwable {
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
				public Collection<String> answer(InvocationOnMock invocation) throws Throwable {
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
			// TODO Auto-generated catch block
			throw new RuntimeException("Shouldn't ever get an exception when building the mock");
		}
		return Pair.with(s, stateMap);
	}

	private DAMLTransactionHandler getDAMLTransactionHandlerInstance() {
		return new DAMLTransactionHandler();
	}

	@Test
	public void testGetVersion() {
		DAMLTransactionHandler handler = this.getDAMLTransactionHandlerInstance();
		final String result = handler.getVersion();
		assertNotNull("getVersion returning null", result);
		assertTrue(String.format("getVersion  Expected: %s Got %s", Namespace.FAMILY_VERSION_1_0, result),
				result.equals(Namespace.FAMILY_VERSION_1_0));
	}

	@Test
	public void testGetNameSpaces() {
		DAMLTransactionHandler handler = this.getDAMLTransactionHandlerInstance();
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
		DAMLTransactionHandler handler = this.getDAMLTransactionHandlerInstance();
		final String result = handler.transactionFamilyName();
		assertNotNull("transactionFamilyName returning null", result);
		assertTrue(String.format("transactionFamilyName Expected: %s Got %s", Namespace.FAMILY_NAME, result),
				result.equals(Namespace.FAMILY_NAME));

	}

	@Test
	public void testApplyEmptyPayload() {
		Pair<State, Map<String, ByteString>> p = getMockState();
		State state = p.getValue0();

		DAMLTransactionHandler handler = this.getDAMLTransactionHandlerInstance();
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

		DAMLTransactionHandler handler = this.getDAMLTransactionHandlerInstance();
		TpProcessRequest transactionRequest = mock(TpProcessRequest.class);
		Pair<State, Map<String, ByteString>> p = getMockState();
		State state = p.getValue0();

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

}
