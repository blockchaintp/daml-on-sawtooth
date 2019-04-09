package com.blockchaintp.noop;

import static org.junit.Assert.*;

import java.io.UnsupportedEncodingException;
import java.util.Collection;

import org.junit.Test;

import static org.mockito.Mockito.*;

import sawtooth.sdk.processor.State;
import sawtooth.sdk.processor.Utils;
import sawtooth.sdk.processor.exceptions.InternalError;
import sawtooth.sdk.processor.exceptions.InvalidTransactionException;
import sawtooth.sdk.protobuf.TpProcessRequest;

public class NoopTransactionHandlerTest {

	@Test
	public void testGetVersion() {
		NoOpTransactionHandler transactionHandler = new NoOpTransactionHandler("test-namespace", "999",
				NoOpTransactionHandler.ALL_OK);
		assertNotNull("getVersion returning null", transactionHandler.getVersion());
		assertTrue("getVersion returning different than constructor", transactionHandler.getVersion().equals("999"));
	}

	@Test
	public void testGetNameSpaces() {
		NoOpTransactionHandler transactionHandler = new NoOpTransactionHandler("test-namespace", "999",
				NoOpTransactionHandler.ALL_OK);
		Collection<String> namespaces = transactionHandler.getNameSpaces();
		assertNotNull("getNameSpaces should not return null", namespaces);
		assertTrue("getNameSpaces collection should be size > 0", namespaces.size() > 0);
		String testValue;
		try {
			testValue = Utils.hash512("test-namespace".getBytes("UTF-8")).substring(0, 6);
			assertTrue("getNameSpaces first element should == Utils.hash512(test-namespace)",
					namespaces.iterator().next().equals(testValue));
		} catch (UnsupportedEncodingException e) {
			fail("Something has gone terribly wrong if UTF-8 is not supported!");
		}

	}

	@Test
	public void testTransactionFamilyName() {
		NoOpTransactionHandler transactionHandler = new NoOpTransactionHandler("test-namespace", "999",
				NoOpTransactionHandler.ALL_OK);
		assertNotNull("transactionFamilyName returning null", transactionHandler.transactionFamilyName());
		assertTrue("transactionFamilyName returning different than constructor",
				transactionHandler.transactionFamilyName().equals("test-namespace"));
	}

	@Test
	public void testApplyAllOKd() {
		NoOpTransactionHandler transactionHandler = new NoOpTransactionHandler("test-namespace", "999",
				NoOpTransactionHandler.ALL_OK);
		
		TpProcessRequest transactionRequest = mock(TpProcessRequest.class);
		State state=mock(State.class);
		
		try {
			transactionHandler.apply(transactionRequest, state);
		} catch (InvalidTransactionException e) {
			fail("InvalidTransactionException recieved when configured for ALL_OK");
		} catch (InternalError e) {
			fail("InternalError recieved when configured for ALL_OK");
		}
	}

	@Test
	public void testApplyAllInvalidTransaction() {
		NoOpTransactionHandler transactionHandler = new NoOpTransactionHandler("test-namespace", "999",
				NoOpTransactionHandler.ALL_INVALID_TRANSACTION);
		
		TpProcessRequest transactionRequest = mock(TpProcessRequest.class);
		State state=mock(State.class);
		
		try {
			transactionHandler.apply(transactionRequest, state);
			fail("OK recieved when configured for ALL_INVALID_TRANSACTION");
		} catch (InvalidTransactionException e) {
			// All good
		} catch (InternalError e) {
			fail("InternalError recieved when configured for ALL_OK");
		}
	}

	@Test
	public void testApplyAllInternalError() {
		NoOpTransactionHandler transactionHandler = new NoOpTransactionHandler("test-namespace", "999",
				NoOpTransactionHandler.ALL_INTERNAL_ERROR);
		
		TpProcessRequest transactionRequest = mock(TpProcessRequest.class);
		State state=mock(State.class);
		
		try {
			transactionHandler.apply(transactionRequest, state);
			fail("OK recieved when configured for ALL_INVALID_ERROR");
		} catch (InvalidTransactionException e) {
			fail("InvalidTransactionException recieved when configured for ALL_INTERNAL_ERROR");
		} catch (InternalError e) {
			// All Good
		}
	}

}
