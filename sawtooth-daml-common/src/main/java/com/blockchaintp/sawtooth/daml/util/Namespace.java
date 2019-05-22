package com.blockchaintp.sawtooth.daml.util;

import com.blockchaintp.utils.SawtoothClientUtils;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntryId;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlStateKey;

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
   * Address space for DamlStateValues.
   */
  public static final String DAML_STATE_VALUE_NS = getNameSpace() + "00";

  /**
   * Address space for Log Entries.
   */
  public static final String DAML_LOG_ENTRY_NS = getNameSpace() + "01";

  /**
   * Address for a list of addresses for Log Entries.
   */
  public static final String DAML_LOG_ENTRY_LIST  = getNameSpace() + "01";

  /**
   * The first 6 characters of the family name hash.
   * @return The first 6 characters of the family name hash
   */
  public static String getNameSpace() {
    return SawtoothClientUtils.getHash(DAML_FAMILY_NAME).substring(0, NAMESPACE_LENGTH);
  }

  /**
   * Make an address given a namespace, and list of parts in order.
   * @param ns    the namespace byte string
   * @param parts one or more string part components
   * @return the hash of the collected address
   */
  public static String makeAddress(final String ns, final String... parts) {
    StringBuilder sb = new StringBuilder();
    for (String p : parts) {
      sb.append(p);
    }
    String hash = SawtoothClientUtils.getHash(sb.toString());
    int begin = hash.length() - ADDRESS_LENGTH + ns.length();
    hash = hash.substring(begin);
    return ns + hash;
  }

  /**
   * Construct a context address for the ledger sync event with logical id
   * eventId.
   * @param entryId the log entry Id
   * @return the byte string address
   */
  public static String makeDamlLogEntryAddress(final DamlLogEntryId entryId) {
    return makeAddress(DAML_LOG_ENTRY_NS, entryId.toByteString().toStringUtf8());
  }

  /**
   * Construct a context address for the given DamlStateKey.
   * @param key DamlStateKey to be used for the address
   * @return the string address
   */
  public static String makeDamlStateAddress(final DamlStateKey key) {
    return makeAddress(DAML_STATE_VALUE_NS, key.toByteString().toStringUtf8());
  }

  /**
   * A utility method to make addresses for most Daml state entry types.
   * @param key the key object which will be used to create the address
   * @return a sawtooth context address
   */
  public static String makeAddressForType(final DamlStateKey key) {
    return makeDamlStateAddress(key);
  }

  /**
   * A utility method to make addresses for most DamlLogEntryId key types,
   * complementing the other methods with similar signature.
   * @param key the key object which will be used to create the address
   * @return a sawtooth context address
   */
  public static String makeAddressForType(final DamlLogEntryId key) {
    return makeDamlLogEntryAddress(key);
  }

  private Namespace() {
  }

}
