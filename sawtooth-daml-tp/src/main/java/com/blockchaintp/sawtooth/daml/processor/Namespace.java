package com.blockchaintp.sawtooth.daml.processor;

import java.io.UnsupportedEncodingException;

import sawtooth.sdk.processor.Utils;

public class Namespace {

	public static final int ADDRESS_LENGTH=70;
	
	public static final String FAMILY_NAME = "daml";
	public static final String FAMILY_VERSION_1_0 = "1.0";

	public static final String DUPLICATE_COMMAND_NS = getNameSpace() + "00";
	public static final String CONTRACT_NS = getNameSpace() + "01";
	public static final String LEDGER_SYNC_EVENT_NS = getNameSpace() + "02";
	public static final String PACKAGE_NS = getNameSpace() + "03";

	public static String getNameSpace() {
		return getHash(FAMILY_NAME).substring(0, 6);
	}

	public static String getHash(String arg) {
		try {
			return Utils.hash512(arg.getBytes("UTF-8"));
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException("Charset UTF-8 is not found! This should never happen.", e);
		}
	}
	
	public static String makeAddress(String ns, String ... parts) {
		int remainder = ADDRESS_LENGTH - ns.length();
		StringBuilder sb=new StringBuilder();
		for (String p: parts ) {
			sb.append(p);
		}
		String hash=getHash(sb.toString()).substring(remainder);
		return hash;
	}

	public static String makeDuplicateCommadAddress(String party, String applicationId, String commandId) {
		return makeAddress(DUPLICATE_COMMAND_NS, party,applicationId,commandId );
	}

	public static String makeLedgerSyncEventAddress(String eventId) {
		return makeAddress(LEDGER_SYNC_EVENT_NS, eventId);
	}
	
	public static String makeContractAddress(String contractId) {
		return makeAddress(CONTRACT_NS, contractId);
	}

	public static String makePackageAddress(String packageId) {
		return makeAddress(PACKAGE_NS, packageId);
	}
	
}
