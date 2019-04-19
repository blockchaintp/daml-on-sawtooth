package com.blockchaintp.sawtooth.daml.procesor;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
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
import com.blockchaintp.sawtooth.daml.processor.impl.DAMLLedgerState;
import com.blockchaintp.sawtooth.daml.protobuf.AcceptedTransaction;
import com.blockchaintp.sawtooth.daml.state.protobuf.Contract;
import com.blockchaintp.sawtooth.daml.state.protobuf.DAMLPackage;
import com.google.protobuf.ByteString;

import sawtooth.sdk.processor.State;
import sawtooth.sdk.processor.exceptions.InternalError;
import sawtooth.sdk.processor.exceptions.InvalidTransactionException;

public class DAMLLedgerStateTest {

	private Pair<State,Map<String,ByteString>> getMockState() {
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
		return Pair.with( s, stateMap);
	}

	@Test
	public void testDuplicateCommand() throws InternalError, InvalidTransactionException {
		Pair<State,Map<String,ByteString>> p= getMockState();
		State mockState=p.getValue0();
		
		DAMLLedgerState damlLedgerState = new DAMLLedgerState(mockState);
		damlLedgerState.setDuplicateCommand("submitterID", "applicationID", "commandID", true);
		assertTrue(damlLedgerState.isDuplicateCommand("submitterID", "applicationID", "commandID"));
		damlLedgerState.setDuplicateCommand("submitterID", "applicationID", "commandID", false);
		assertFalse(damlLedgerState.isDuplicateCommand("submitterID", "applicationID", "otherCommand"));

	}

	@Test
	public void testContract() throws InternalError, InvalidTransactionException {
		Pair<State,Map<String,ByteString>> p= getMockState();
		State mockState=p.getValue0();

		String archivedContractAddress = Namespace.makeContractAddress("archivedcontract");
		String activeContractAddress = Namespace.makeContractAddress("activecontract");
		Contract activeContract = Contract.getDefaultInstance().toBuilder().setArchived(false).build();
		Contract archivedContract = Contract.getDefaultInstance().toBuilder().setArchived(true).build();
		Map<String, ByteString> contractMap = new HashMap<>();
		contractMap.put(archivedContractAddress, archivedContract.toByteString());
		contractMap.put(activeContractAddress, activeContract.toByteString());

		mockState.setState(contractMap.entrySet());

		DAMLLedgerState damlLedgerState = new DAMLLedgerState(mockState);
		damlLedgerState.setContract(activeContract);

		assertNotNull(damlLedgerState.getContract("activecontract"));
		assertTrue(damlLedgerState.getContract("activecontract") instanceof Contract);
		assertTrue(damlLedgerState.isActiveContract("activecontract"));
		assertFalse(damlLedgerState.isActiveContract("archivedcontract"));

		damlLedgerState.setActiveContract("archivedcontract", false);
		assertTrue(damlLedgerState.isActiveContract("archivedcontract"));

		assertNull(damlLedgerState.getContract("nocontract"));

	}

	@Test
	public void testPackage() throws InternalError, InvalidTransactionException {
		Pair<State,Map<String,ByteString>> p= getMockState();
		State mockState=p.getValue0();
		
		String packageAddress = Namespace.makePackageAddress("mypackage");
		Map<String,ByteString> packageMap=new HashMap<>();
		DAMLPackage pkg=DAMLPackage.getDefaultInstance();
		packageMap.put(packageAddress,pkg.toByteString());
		
		mockState.setState(packageMap.entrySet());

		DAMLLedgerState damlLedgerState = new DAMLLedgerState(mockState);
		damlLedgerState.setDAMLPackage(pkg,"thispackageId");
		assertNotNull(damlLedgerState.getDAMLPackage("thispackageId"));
		assertNull(damlLedgerState.getDAMLPackage("unknownpackageId"));
	}

	@Test 
	public void testLedgerSyncEvent() {
		Pair<State,Map<String,ByteString>> p= getMockState();
		State mockState=p.getValue0();
		Map<String,ByteString> stateMap=p.getValue1();
		
		AcceptedTransaction at=AcceptedTransaction.getDefaultInstance();
		DAMLLedgerState damlLedgerState = new DAMLLedgerState(mockState);
		
		damlLedgerState.addLedgerSyncEvent(at);
		assertTrue(stateMap.containsValue(at.toByteString()));
		
		
	}
}
