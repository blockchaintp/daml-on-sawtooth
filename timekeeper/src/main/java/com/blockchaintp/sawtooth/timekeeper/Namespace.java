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
package com.blockchaintp.sawtooth.timekeeper;

import com.blockchaintp.utils.SawtoothClientUtils;

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
   * The first 6 characters of the family name hash.
   * @return The first 6 characters of the family name hash
   */
  public static String getNameSpace() {
    return SawtoothClientUtils.getHash(TIMEKEEPER_FAMILY_NAME).substring(0, NAMESPACE_LENGTH);
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

  private Namespace() {
  }

}
