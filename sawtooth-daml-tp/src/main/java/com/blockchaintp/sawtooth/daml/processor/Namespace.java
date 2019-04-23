package com.blockchaintp.sawtooth.daml.processor;

import java.io.UnsupportedEncodingException;

import sawtooth.sdk.processor.Utils;

/**
 * Utility class dealing with the common namespace functions and values with
 * Sawtooth and DAML.
 * @author scealiontach
 */
public final class Namespace {
  private Namespace() {
  }

  /**
   * Sawtooth Namespaces are 6 chars long.
   */
  public static final int NAMESPACE_LENGTH = 6;

  /**
   * The total length of a sawtooth address is 70 chars.
   */
  public static final int ADDRESS_LENGTH = 70;

  /**
   * The DAML family name "daml".
   */
  public static final String FAMILY_NAME = "daml";

  /**
   * Family Version 1.0 .
   */
  public static final String FAMILY_VERSION_1_0 = "1.0";

  /**
   * Address space for duplicate command records.
   */
  public static final String DUPLICATE_COMMAND_NS = getNameSpace() + "00";

  /**
   * Address space for contract entries.
   */
  public static final String CONTRACT_NS = getNameSpace() + "01";

  /**
   * Address space for ledger sync events.
   */
  public static final String LEDGER_SYNC_EVENT_NS = getNameSpace() + "02";

  /**
   * Address space for package entries.
   */
  public static final String PACKAGE_NS = getNameSpace() + "03";

  /**
   * The first 6 characters of the family name hash.
   * @return The first 6 characters of the family name hash
   */
  public static String getNameSpace() {
    return getHash(FAMILY_NAME).substring(0, NAMESPACE_LENGTH);
  }

  /**
   * For a given string return its hash512, transform the encoding problems into
   * RuntimeErrors.
   * @param arg a string
   * @return the hash512 of the string
   */
  public static String getHash(final String arg) {
    try {
      return Utils.hash512(arg.getBytes("UTF-8"));
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException("Charset UTF-8 is not found! This should never happen.", e);
    }
  }

  /**
   * Make an address given a namespace, and list of parts in order.
   * @param ns    the namespace byte string
   * @param parts one or more string part components
   * @return the hash of the collected address
   */
  public static String makeAddress(final String ns, final String... parts) {
    int remainder = ADDRESS_LENGTH - ns.length();
    StringBuilder sb = new StringBuilder();
    for (String p : parts) {
      sb.append(p);
    }
    String hash = getHash(sb.toString()).substring(remainder);
    return ns + hash;
  }

  /**
   * Construct a context address for duplication command identified the arguments provided.
   * @param party the submitter of the command
   * @param applicationId the application id of the command
   * @param commandId the id of the commmand
   * @return the byte string address
   */
  public static String makeDuplicateCommadAddress(final String party, final String applicationId,
      final String commandId) {
    return makeAddress(DUPLICATE_COMMAND_NS, party, applicationId, commandId);
  }

  /**
   * Construct a context address for the ledger sync event with logical id eventId.
   * @param eventId the logical event Id
   * @return the byte string address
   */
  public static String makeLedgerSyncEventAddress(final String eventId) {
    return makeAddress(LEDGER_SYNC_EVENT_NS, eventId);
  }

  /**
   * Construct a context address for the contract with logical id .
   * @param contractId the logical contract Id
   * @return the byte string address
   */
  public static String makeContractAddress(final String contractId) {
    return makeAddress(CONTRACT_NS, contractId);
  }

  /**
   * Construct a context address for the package with logical id .
   * @param packageId the logical package Id
   * @return the byte string address
   */
  public static String makePackageAddress(final String packageId) {
    return makeAddress(PACKAGE_NS, packageId);
  }

}
