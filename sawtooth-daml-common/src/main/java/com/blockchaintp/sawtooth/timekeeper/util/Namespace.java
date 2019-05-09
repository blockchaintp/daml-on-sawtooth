package com.blockchaintp.sawtooth.timekeeper.util;

import java.io.UnsupportedEncodingException;

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
   * The TimeKeeper family name "daml".
   */
  public static final String TIMEKEEPER_FAMILY_NAME = "timekeeper";

  /**
   * Family Version 1.0 .
   */
  public static final String TIMEKEEPER_FAMILY_VERSION_1_0 = "1.0";

  /**
   * Address space for individual TimeKeeper records.
   */
  public static final String TIMEKEEPER_RECORD_NS = getNameSpace() + "00";

  /**
   * Address for global timekeeper record.
   */
  public static final String TIMEKEEPER_GLOBAL_RECORD = makeAddress(TIMEKEEPER_RECORD_NS, "Global Record");

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
    return getHash(TIMEKEEPER_FAMILY_NAME).substring(0, NAMESPACE_LENGTH);
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

  private Namespace() {
  }

}
