package com.blockchaintp.utils;

import java.util.HashMap;
import java.util.Map;

import sawtooth.sdk.signing.Context;
import sawtooth.sdk.signing.CryptoFactory;
import sawtooth.sdk.signing.PrivateKey;
import sawtooth.sdk.signing.PublicKey;

/**
 * An implementation of an in-memory key manager and signer.
 */
public final class InMemoryKeyManager implements KeyManager {

  private static final String DEFAULT = "default";

  /**
   * Creates an instance of secp256k1 based manager with a random private key and
   * corresponding public key.
   * @return KeyManager.
   */
  public static KeyManager create() {
    Context ctx = CryptoFactory.createContext("secp256k1");
    PrivateKey privKey = ctx.newRandomPrivateKey();
    PublicKey pubKey = ctx.getPublicKey(privKey);
    KeyManager kmgr = new InMemoryKeyManager(privKey, pubKey);
    return kmgr;
  }

  /**
   * Creates an instance of secp256k1 based manager with a random private key and
   * corresponding public key.
   * @param privKey the default private key for this manager
   * @param pubKey  the default public key for this manager
   * @return KeyManager.
   */
  public static KeyManager create(final PrivateKey privKey, final PublicKey pubKey) {
    return new InMemoryKeyManager(privKey, pubKey);
  }

  private final Map<String, PrivateKey> privateKeyMap;
  private final Map<String, PublicKey> publicKeyMap;

  private InMemoryKeyManager(final PrivateKey privKey, final PublicKey pubKey) {
    this.privateKeyMap = new HashMap<>();
    this.publicKeyMap = new HashMap<>();
    this.privateKeyMap.put(DEFAULT, privKey);
    this.publicKeyMap.put(DEFAULT, pubKey);
  }

  @Override
  public PublicKey getPublicKey() {
    return this.publicKeyMap.get(DEFAULT);
  }

  @Override
  public String getPublicKeyInHex() {
    return this.getPublicKey().hex();
  }

  private Context getContextForKey(final PublicKey key) {
    return CryptoFactory.createContext(key.getAlgorithmName());
  }

  private Context getContextForKey(final PrivateKey key) {
    return CryptoFactory.createContext(key.getAlgorithmName());
  }

  @Override
  public String sign(final byte[] item) {
    return sign(DEFAULT, item);
  }

  @Override
  public String sign(final String id, final byte[] item) {
    PrivateKey privKey = this.privateKeyMap.get(id);
    if (privKey == null) {
      throw new RuntimeException(String.format("No private key with id %s is available", id));
    }
    return getContextForKey(privKey).sign(item, privKey);
  }

  @Override
  public PublicKey getPublicKey(final String id) {
    return this.publicKeyMap.get(id);
  }

  @Override
  public String getPublicKeyInHex(final String id) {
    return getPublicKey(id).hex();
  }

  @Override
  public boolean verify(final byte[] item, final String signature) {
    return verify(DEFAULT, item, signature);
  }

  @Override
  public boolean verify(final String id, final byte[] item, final String signature) {
    PublicKey pubKey = this.getPublicKey(id);
    if (pubKey == null) {
      throw new RuntimeException(String.format("No public key with id %s is available", id));
    }
    return getContextForKey(pubKey).verify(signature, item, pubKey);
  }

  @Override
  public boolean verify(final PublicKey pubKey, final byte[] item, final String signature) {
    return getContextForKey(pubKey).verify(signature, item, pubKey);
  }
}
