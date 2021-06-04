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
package com.blockchaintp.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sawtooth.sdk.signing.CryptoFactory;
import sawtooth.sdk.signing.PrivateKey;
import sawtooth.sdk.signing.PublicKey;

/**
 * An implementation of an in-memory key manager and signer.
 */
public final class InMemoryKeyManager extends BaseKeyManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(InMemoryKeyManager.class);

  /**
   * Creates an instance of secp256k1 based manager with a random private key and
   * corresponding public key.
   * @return KeyManager.
   */
  public static KeyManager create() {
    LOGGER.info("Creating InMemoryKeyManager with new random default keys");
    var ctx = CryptoFactory.createContext("secp256k1");
    var privKey = ctx.newRandomPrivateKey();
    var pubKey = ctx.getPublicKey(privKey);
    return new InMemoryKeyManager(privKey, pubKey);
  }

  /**
   * Creates an instance of secp256k1 based manager with a random private key and
   * corresponding public key.
   * @param privKey the default private key for this manager
   * @param pubKey  the default public key for this manager
   * @return KeyManager.
   */
  public static KeyManager create(final PrivateKey privKey, final PublicKey pubKey) {
    LOGGER.info("Creating InMemoryKeyManager with provided keys");
    return new InMemoryKeyManager(privKey, pubKey);
  }

  private InMemoryKeyManager(final PrivateKey privKey, final PublicKey pubKey) {
    super();
    putKey(privKey);
    putKey(pubKey);
  }

}
