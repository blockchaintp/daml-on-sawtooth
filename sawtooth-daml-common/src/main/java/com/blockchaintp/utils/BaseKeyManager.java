package com.blockchaintp.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sawtooth.sdk.signing.Context;
import sawtooth.sdk.signing.CryptoFactory;
import sawtooth.sdk.signing.PrivateKey;
import sawtooth.sdk.signing.PublicKey;

public abstract class BaseKeyManager implements KeyManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(BaseKeyManager.class);

  private static final String DEFAULT = "default";

  private final Map<String, PrivateKey> privateKeyMap;
  private final Map<String, PublicKey> publicKeyMap;

  BaseKeyManager() {
    this.privateKeyMap = new HashMap<>();
    this.publicKeyMap = new HashMap<>();
  }

  @Override
  public PublicKey getPublicKey() {
    return getPublicKey(DEFAULT);
  }

  @Override
  public String getPublicKeyInHex() {
    return getPublicKey(DEFAULT).hex();
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
  public String sign(final byte[] item) {
    return sign(DEFAULT, item);
  }

  @Override
  public String sign(final String id, final byte[] item) {
    PrivateKey privKey = getPrivateKey(id);
    if (privKey == null) {
      throw new KeyManagerRuntimeException(String.format("No private key with id %s is available", id));
    }
    LOGGER.debug("Signing array of size={} for id={}", item.length, id);
   return getContextForKey(privKey).sign(item, privKey);
  }

  @Override
  public boolean verify(final byte[] item, final String signature) {
    return verify(DEFAULT, item, signature);
  }

  @Override
  public boolean verify(final String id, final byte[] item, final String signature) {
    var pubKey = this.getPublicKey(id);
    if (pubKey == null) {
      throw new KeyManagerRuntimeException(String.format("No public key with id %s is available", id));
    }
    return getContextForKey(pubKey).verify(signature, item, pubKey);
  }

  @Override
  public boolean verify(final PublicKey pubKey, final byte[] item, final String signature) {
    return getContextForKey(pubKey).verify(signature, item, pubKey);
  }

  protected PrivateKey putKey(PrivateKey key) {
    return putKey(DEFAULT, key);
  }

  protected PrivateKey putKey(String id, PrivateKey key) {
    return this.privateKeyMap.put(id, key);
  }

  protected PublicKey putKey(PublicKey key) {
    return putKey(DEFAULT, key);
  }

  protected PublicKey putKey(String id, PublicKey key) {
    return this.publicKeyMap.put(id, key);
  }

  protected PrivateKey getPrivateKey(final String id) {
    return this.privateKeyMap.get(id);
  }

  private Context getContextForKey(final PublicKey key) {
    return CryptoFactory.createContext(key.getAlgorithmName());
  }

  private Context getContextForKey(final PrivateKey key) {
    LOGGER.debug("Getting context for algorithm={}", key.getAlgorithmName());
    return CryptoFactory.createContext(key.getAlgorithmName());
  }

  protected boolean hasDefaultPrivateKey() {
    return this.privateKeyMap.containsKey(DEFAULT);
  }

  protected boolean hasPrivateKey(String id) {
    return this.privateKeyMap.containsKey(id);
  }

  protected boolean hasDefaultPublicKey() {
    return this.publicKeyMap.containsKey(DEFAULT);
  }

  protected boolean hasPublicKey(String id) {
    return this.publicKeyMap.containsKey(id);
  }

  protected Set<Entry<String,PrivateKey>> privateKeys() {
    return this.privateKeyMap.entrySet();
  }

  protected Set<Entry<String,PublicKey>> publicKeys() {
    return this.publicKeyMap.entrySet();
  }

  protected void fillPublicKeyForDefaultPrivate() {
    fillPublicKeyForPrivate(DEFAULT);
  }

  protected void fillPublicKeyForPrivate(String id) {
    fillPublicKeyForPrivate(id, false);
  }

  protected void fillPublicKeyForPrivate(String id, boolean force) {
    if (force || !hasPublicKey(id)) {
      var pk = getPrivateKey(id);
      String algorithmName = pk.getAlgorithmName();
      var ctx = CryptoFactory.createContext(algorithmName);
      var publicKey = ctx.getPublicKey(pk);
      putKey(id, publicKey);
    }
  }

  protected void fillRandomPrivateKey(String id, String algorithm) {
    if (!hasPrivateKey(id)) {
      var ctx = CryptoFactory.createContext(algorithm);
      var privKey = ctx.newRandomPrivateKey();
      putKey(id, privKey);
      fillPublicKeyForPrivate(id, true);
    }
  }

  protected void fillRandomPrivateKey(String algorithm) {
    fillRandomPrivateKey(DEFAULT, algorithm);
  }

}
