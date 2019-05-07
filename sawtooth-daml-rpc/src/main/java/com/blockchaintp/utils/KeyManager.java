package com.blockchaintp.utils;

import sawtooth.sdk.signing.Context;
import sawtooth.sdk.signing.CryptoFactory;
import sawtooth.sdk.signing.PrivateKey;
import sawtooth.sdk.signing.PublicKey;
import sawtooth.sdk.signing.Signer;

/**
 * An implementation of an in-memory key manager and signer.
 *
 * @author paulwizviz
 *
 */
public final class KeyManager {

  /**
   *
   * @return KeyManager.
   */
  public static KeyManager createSECP256k1() {
    return new KeyManager("secp256k1");
  }

  private PrivateKey privateKey;
  private PublicKey publicKey;
  private Signer signer;

  private KeyManager(final String algoName) {
    Context context = CryptoFactory.createContext(algoName);
    this.privateKey = context.newRandomPrivateKey();
    this.publicKey = context.getPublicKey(privateKey);
    this.signer = new Signer(context, privateKey);
  }

  /**
   *
   * @return PrivateKey.
   */
  public PrivateKey getPrivateKey() {
    return this.privateKey;
  }

  /**
   *
   * @return PrivateKey.
   */
  public PublicKey getPublicKey() {
    return this.publicKey;
  }

  /**
   *
   * @return String representation of the hex value of public key.
   */
  public String getPublicKeyInHex() {
    return this.getPublicKey().hex();
  }

  /**
   *
   * @param item to be signed.
   * @return String representation of signed item.
   */
  public String sign(final byte[] item) {
    return this.signer.sign(item);
  }
}
