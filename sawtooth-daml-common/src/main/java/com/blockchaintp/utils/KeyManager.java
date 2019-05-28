package com.blockchaintp.utils;

import sawtooth.sdk.signing.PublicKey;

/**
 * An interface to represent an identity.
 */
public interface KeyManager {

  /**
   * An accessor to the default public key owned by this manager.
   * @return PublicKey.
   */
  PublicKey getPublicKey();

  /**
   * An accessor to the default public key owned by this manager.
   * @param id the id of the key in question
   * @return PublicKey or null if the key does not exist
   */
  PublicKey getPublicKey(String id);

  /**
   * An accessor to the hex value of the default public key owned by this manager.
   * @return String representation of the hex value of public key.
   */
  String getPublicKeyInHex();

  /**
   * An accessor to the hex value of the specified public key owned by this
   * manager.
   * @param id the id of the key in question.
   * @return String representation of the hex value of public key or null if it does not exist.
   */
  String getPublicKeyInHex(String id);

  /**
   * Sign the item using the default private key of this manager.
   * @param item to be signed.
   * @return String representation of signed item.
   */
  String sign(byte[] item);

  /**
   * Sign the item using the designated private key owned by this manager.
   * @param id the id of the key to use
   * @param item to be signed.
   * @return String representation of signed item.
   */
  String sign(String id, byte[] item);

  /**
   * verify the item's signature using the default key pair owned by this manager.
   * @param item the item to be verifies
   * @param signature the signature to verify
   * @return true if the signature is valid, otherwise false
   */
  boolean verify(byte[] item, String signature);

  /**
   * verify the item's signature using the designated key pair owned by this
   * manager.
   * @param id the id of the key to use to verify the signature.
   * @param item the item to verify
   * @param signature the signature to verify
   * @return true if the signature is valid, otherwise false
   */
  boolean verify(String id, byte[] item, String signature);

  /**
   * verify the item's signature using an arbitrary key.
   * @param key the key to use to verify the signature.
   * @param item the item to verify
   * @param signature the signature to verify
   * @return true if the signature is valid, otherwise false
   */
  boolean verify(PublicKey key, byte[] item, String signature);

}
