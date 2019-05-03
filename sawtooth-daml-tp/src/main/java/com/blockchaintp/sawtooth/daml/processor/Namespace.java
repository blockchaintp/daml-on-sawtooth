package com.blockchaintp.sawtooth.daml.processor;

import java.io.UnsupportedEncodingException;

import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlCommandDedupKey;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlContractId;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntryId;

import sawtooth.sdk.processor.Utils;

/**
 * Utility class dealing with the common namespace functions and values with
 * Sawtooth and DAML.
 * @author scealiontach
 */
public final class Namespace {
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
  public static final String DAML_FAMILY_NAME = "daml";

  /**
   * Family Version 1.0 .
   */
  public static final String DAML_FAMILY_VERSION_1_0 = "1.0";

  /**
   * Address space for duplicate command records.
   */
  public static final String DAML_DUPLICATE_COMMAND_NS = getNameSpace() + "00";

  /**
   * Address space for contract entries.
   */
  public static final String DAML_CONTRACT_NS = getNameSpace() + "01";

  /**
   * Address space for Log Entries.
   */
  public static final String DAML_LOG_ENTRY_NS = getNameSpace() + "02";

  /**
   * Address space for package entries.
   */
  public static final String DAML_PACKAGE_NS = getNameSpace() + "03";

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
   * The first 6 characters of the family name hash.
   * @return The first 6 characters of the family name hash
   */
  public static String getNameSpace() {
    return getHash(DAML_FAMILY_NAME).substring(0, NAMESPACE_LENGTH);
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
   * Construct a context address for DamlCommandDedupKey provided.
   * @param key the command dedup key
   * @return the byte string address
   */
  public static String makeDamlCommadDedupAddress(final DamlCommandDedupKey key) {
    return makeAddress(DAML_DUPLICATE_COMMAND_NS, key.getSubmitter(), key.getApplicationId(), key.getCommandId());
  }

  /**
   * Construct a context address for the contract with logical id .
   * @param contractId the logical contract Id
   * @return the byte string address
   */
  public static String makeDamlContractAddress(final DamlContractId contractId) {
    return makeAddress(DAML_CONTRACT_NS, String.valueOf(contractId.getNodeId()));
  }

  /**
   * Construct a context address for the ledger sync event with logical id
   * eventId.
   * @param entryId the logical event Idarg0)
   * @return the byte string address
   */
  public static String makeDamlLogEntryAddress(final DamlLogEntryId entryId) {
    return makeAddress(DAML_LOG_ENTRY_NS, entryId.getEntryId().toStringUtf8());
  }

  /**
   * Construct a context address for the package with logical id .
   * @param packageId the logical package Id
   * @return the byte string address
   */
  public static String makeDamlPackageAddress(final String packageId) {
    return makeAddress(DAML_PACKAGE_NS, packageId);
  }

  public static String makeAddressForType(final Object keyObject) {
    if (DamlContractId.class.equals(keyObject.getClass())) {
      return makeDamlContractAddress((DamlContractId) keyObject);
    } else if (DamlLogEntryId.class.equals(keyObject.getClass())) {
      return makeDamlLogEntryAddress((DamlLogEntryId) keyObject);
    } else if (DamlCommandDedupKey.class.equals(keyObject.getClass())) {
      return makeDamlCommadDedupAddress((DamlCommandDedupKey) keyObject);
    } else if (String.class.equals(keyObject.getClass())) {
      return makeDamlPackageAddress((String) keyObject);
    } else {
      return null;
    }
  }

  private Namespace() {
  }

}
