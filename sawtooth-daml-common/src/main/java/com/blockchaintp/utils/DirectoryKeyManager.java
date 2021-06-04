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
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import com.google.protobuf.ByteString;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sawtooth.sdk.signing.PrivateKey;
import sawtooth.sdk.signing.PublicKey;
import sawtooth.sdk.signing.Secp256k1PrivateKey;
import sawtooth.sdk.signing.Secp256k1PublicKey;

/**
 * The DirectoryKeyManager maintains a collection of keys in a specific
 * filesystem path.
 */
public final class DirectoryKeyManager extends BaseKeyManager {

  private static final String SECP256K1 = "secp256k1";

  private static final String PRIV_KEYFILE_EXT = ".priv";

  private static final Logger LOGGER = LoggerFactory.getLogger(DirectoryKeyManager.class);

  /**
   * Creates an instance of secp256k1 based manager with a random private key and
   * corresponding public key.
   *
   * @param path the path where this manager will store its keys
   * @return KeyManager.
   * @throws IOException there was a problem initializing the key manager
   */
  public static KeyManager create(final String path) throws IOException {
    var kmgr = new DirectoryKeyManager(path, SECP256K1);
    kmgr.initialize();
    return kmgr;
  }

  private final File keystorePath;
  private final String algorithmName;

  private DirectoryKeyManager(final String path, final String algoName) {
    super();
    this.algorithmName = algoName;
    this.keystorePath = new File(path);
    LOGGER.info("Created DirectoryKeyManager at {}, please make sure this directory is secure", path);
    this.keystorePath.mkdirs();
    if (!this.keystorePath.isDirectory()) {
      throw new KeyManagerRuntimeException(String.format("keyStorePath is not a directory: %s", this.keystorePath));
    }
  }

  @SuppressWarnings("java:S3824")
  private void initialize() throws IOException {
    scanDirectory();
    fillRandomPrivateKey(this.algorithmName);
    flushKeys();
  }

  private void flushKeys() throws IOException {
    flushPrivateKeys();
    flushPublicKeys();
  }

  private void flushPublicKeys() throws IOException {
    for (Entry<String, PublicKey> entry : publicKeys()) {
      var keyDir = new File(keystorePath, entry.getKey());
      if (!keyDir.exists()) {
        if (keyDir.mkdirs()) {
          var privFile = new File(keyDir, entry.getKey() + ".pub");
          try (var fos = new FileOutputStream(privFile)) {
            var bs = ByteString.copyFromUtf8(entry.getValue().hex());
            fos.write(bs.toByteArray());
            fos.flush();
          }
        } else {
          throw new IOException("Failed to create directory " + keyDir.getAbsolutePath());
        }
      } else {
        var privFile = new File(keyDir, entry.getKey() + ".pub");
        if (!privFile.exists()) {
          try (var fos = new FileOutputStream(privFile)) {
            var bs = ByteString.copyFromUtf8(entry.getValue().hex());
            fos.write(bs.toByteArray());
            fos.flush();
          }
        }
      }
    }
  }

  private void flushPrivateKeys() throws IOException {
    for (Entry<String, PrivateKey> entry : privateKeys()) {
      var keyDir = new File(keystorePath, entry.getKey());
      if (!keyDir.exists()) {
        if (keyDir.mkdirs()) {
          var privFile = new File(keyDir, entry.getKey() + PRIV_KEYFILE_EXT);
          try (var fos = new FileOutputStream(privFile)) {
            var bs = ByteString.copyFromUtf8(entry.getValue().hex());
            fos.write(bs.toByteArray());
            fos.flush();
          }
        } else {
          throw new IOException("Failed to create directory " + keyDir.getAbsolutePath());
        }
      } else {
        var privFile = new File(keyDir, entry.getKey() + PRIV_KEYFILE_EXT);
        if (!privFile.exists()) {
          try (var fos = new FileOutputStream(privFile)) {
            var bs = ByteString.copyFromUtf8(entry.getValue().hex());
            fos.write(bs.toByteArray());
            fos.flush();
          }
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
      loadKeyPair(f);
    }
  }

  private void loadKeyPair(final File f) throws IOException {
    String id = f.getName();
    File[] privateKeys = f.listFiles(new EndsWithFilter(PRIV_KEYFILE_EXT));
    if (privateKeys.length > 1) {
      throw new KeyManagerRuntimeException(String.format("too many private key files in %s", f.getAbsolutePath()));
    }
    for (File p : privateKeys) {
      byte[] data = readKeyFile(p);
      var hexPk = ByteString.copyFrom(data).toStringUtf8();
      PrivateKey pk = Secp256k1PrivateKey.fromHex(hexPk);
      putKey(id, pk);
    }
    File[] publicKeys = f.listFiles(new EndsWithFilter(".pub"));
    if (publicKeys.length > 1) {
      throw new KeyManagerRuntimeException(String.format("too many private key files in %s", f.getAbsolutePath()));
    }
    for (File p : privateKeys) {
      byte[] data = readKeyFile(p);
      var hexPk = ByteString.copyFrom(data).toStringUtf8();
      PublicKey pk = Secp256k1PublicKey.fromHex(hexPk);
      putKey(id, pk);
    }
  }

  private byte[] readKeyFile(final File p) throws IOException {
    try (var fis = new FileInputStream(p)) {
      return fis.readAllBytes();
    }
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
