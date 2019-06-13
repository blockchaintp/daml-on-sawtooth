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

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import sawtooth.sdk.signing.Context;
import sawtooth.sdk.signing.CryptoFactory;
import sawtooth.sdk.signing.PrivateKey;
import sawtooth.sdk.signing.PublicKey;
import sawtooth.sdk.signing.Secp256k1PrivateKey;
import sawtooth.sdk.signing.Secp256k1PublicKey;

/**
 * The DirectoryKeyManager maintains a collection of keys in a specific
 * filesystem path.
 */
public final class DirectoryKeyManager implements KeyManager {

  private static final String DEFAULT = "default";

  /**
   * Creates an instance of secp256k1 based manager with a random private key and
   * corresponding public key.
   * @param path the path where this manager will store its keys
   * @return KeyManager.
   * @throws IOException there was a problem initializing the key manager
   */
  public static KeyManager create(final String path) throws IOException {
    DirectoryKeyManager kmgr = new DirectoryKeyManager(path, "secp256k1");
    kmgr.initialize();
    return kmgr;
  }

  private final File keystorePath;
  private final Map<String, PrivateKey> privateKeyMap;
  private final Map<String, PublicKey> publicKeyMap;
  private final String algorithmName;

  private DirectoryKeyManager(final String path, final String algoName) throws IOException {
    this.algorithmName = algoName;
    this.keystorePath = new File(path);
    this.keystorePath.mkdirs();
    if (!this.keystorePath.isDirectory()) {
      throw new RuntimeException(String.format("keyStorePath is not a directory: %s", this.keystorePath));
    }
    this.privateKeyMap = new HashMap<>();
    this.publicKeyMap = new HashMap<>();
  }

  private void initialize() throws IOException {
    scanDirectory();
    if (!this.privateKeyMap.containsKey(DEFAULT)) {
      Context ctx = CryptoFactory.createContext(this.algorithmName);
      PrivateKey privKey = ctx.newRandomPrivateKey();
      this.privateKeyMap.put(DEFAULT, privKey);
    }
    if (!this.publicKeyMap.containsKey(DEFAULT)) {
      Context ctx = CryptoFactory.createContext(this.algorithmName);
      PublicKey publicKey = ctx.getPublicKey(this.privateKeyMap.get(DEFAULT));
      this.publicKeyMap.put(DEFAULT, publicKey);
    }
    flushKeys();
  }

  private void flushKeys() throws IOException {
    flushPrivateKeys();
    flushPublicKeys();
  }

  private void flushPublicKeys() throws FileNotFoundException, IOException {
    for (Entry<String, PublicKey> entry : this.publicKeyMap.entrySet()) {
      File keyDir = new File(keystorePath, entry.getKey());
      if (!keyDir.exists()) {
        if (keyDir.mkdirs()) {
          File privFile = new File(keyDir, entry.getKey() + ".pub");
          FileOutputStream fos = new FileOutputStream(privFile);
          fos.write(entry.getValue().getBytes());
          fos.flush();
          fos.close();
        } else {
          throw new IOException("Failed to create directory " + keyDir.getAbsolutePath());
        }
      } else {
        File privFile = new File(keyDir, entry.getKey() + ".pub");
        if (!privFile.exists()) {
          FileOutputStream fos = new FileOutputStream(privFile);
          fos.write(entry.getValue().getBytes());
          fos.flush();
          fos.close();
        }
      }
    }
  }

  private void flushPrivateKeys() throws FileNotFoundException, IOException {
    for (Entry<String, PrivateKey> entry : this.privateKeyMap.entrySet()) {
      File keyDir = new File(keystorePath, entry.getKey());
      if (!keyDir.exists()) {
        if (keyDir.mkdirs()) {
          File privFile = new File(keyDir, entry.getKey() + ".priv");
          FileOutputStream fos = new FileOutputStream(privFile);
          fos.write(entry.getValue().getBytes());
          fos.flush();
          fos.close();
        } else {
          throw new IOException("Failed to create directory " + keyDir.getAbsolutePath());
        }
      } else {
        File privFile = new File(keyDir, entry.getKey() + ".priv");
        if (!privFile.exists()) {
          FileOutputStream fos = new FileOutputStream(privFile);
          fos.write(entry.getValue().getBytes());
          fos.flush();
          fos.close();
        }
      }
    }
  }

  private void scanDirectory() throws IOException {
    if (!keystorePath.exists() && !keystorePath.mkdirs()) {
      throw new IOException("failed to mkdir " + keystorePath);
    }

    File[] children = this.keystorePath.listFiles();
    List<File> keyDirectories = new ArrayList<>();
    for (File f : children) {
      if (f.isDirectory()) {
        keyDirectories.add(f);
      }
    }
    for (File f : keyDirectories) {
      getKeyPair(f);
    }
  }

  private void getKeyPair(final File f) throws IOException {
    String id = f.getName();
    File[] privateKeys = f.listFiles(new EndsWithFilter(".priv"));
    if (privateKeys.length > 1) {
      throw new RuntimeException(String.format("too many private key files in %s", f.getAbsolutePath()));
    }
    for (File p : privateKeys) {
      byte[] data = readKeyFile(p);
      PrivateKey pk = new Secp256k1PrivateKey(data);
      this.privateKeyMap.put(id, pk);
    }
    File[] publicKeys = f.listFiles(new EndsWithFilter(".pub"));
    if (publicKeys.length > 1) {
      throw new RuntimeException(String.format("too many private key files in %s", f.getAbsolutePath()));
    }
    for (File p : privateKeys) {
      byte[] data = readKeyFile(p);
      PublicKey pk = new Secp256k1PublicKey(data);
      this.publicKeyMap.put(id, pk);
    }
  }

  private byte[] readKeyFile(final File p) throws IOException {
    FileInputStream fis = new FileInputStream(p);
    byte[] data = fis.readAllBytes();
    fis.close();
    return data;
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

  /**
   * A filter which accepts files based on a suffix.
   */
  class EndsWithFilter implements FileFilter {
    private String suffix;

    EndsWithFilter(final String endStr) {
      this.suffix = endStr;
    }

    @Override
    public boolean accept(final File file) {
      return file.getName().endsWith(this.suffix);
    }

  }
}
