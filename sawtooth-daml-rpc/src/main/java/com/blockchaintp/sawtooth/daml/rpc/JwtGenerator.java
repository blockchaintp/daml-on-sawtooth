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

package com.blockchaintp.sawtooth.daml.rpc;

import java.io.File;
import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.Security;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPrivateKeySpec;
import java.util.Scanner;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

import akka.protobuf.ByteString;

public class JwtGenerator {

  private ECPrivateKey privateKey;
  private ECPublicKey publicKey;

  public JwtGenerator(final String privKeyFilename) {
    try {
      extractKeys(privKeyFilename);
    } catch (final Exception e) {
      this.privateKey = null;
      this.publicKey = null;
    }
  }

  public boolean isUsable() {
    return (this.privateKey != null && this.publicKey != null) ? true : false;
  }

  public ECPrivateKey getPrivateKey() {
    return this.privateKey;
  }

  public ECPublicKey getPublicKey() {
    return this.publicKey;
  }

  public String generateToken() {
    Algorithm ecdsa512Algorithm = Algorithm.ECDSA256(this.publicKey, this.privateKey);
    String token = JWT.create().sign(ecdsa512Algorithm);
    return token;
  }

  private void extractKeys(final String privKeyFilename) throws Exception {

    final File privKeyFile = new File(privKeyFilename);

    try {

      // Read private key file and it's content
      final Scanner scanner = new Scanner(privKeyFile);
      final String privKeyString = scanner.nextLine();
      ByteString privKeyBs = ByteString.copyFromUtf8(privKeyString);
      Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());

      AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
      parameters.init(new ECGenParameterSpec("secp256k1"));

      ECParameterSpec params = parameters.getParameterSpec(ECParameterSpec.class);

     ECPrivateKeySpec privKeySpec = new ECPrivateKeySpec(new BigInteger(1, privKeyBs.toByteArray()), params);
      // Obtain key factory
      final KeyFactory keyFactory = KeyFactory.getInstance("EC");

      // Generate Java security competible private and public key types.
      this.privateKey = (ECPrivateKey) keyFactory.generatePrivate(privKeySpec);
      scanner.close();

    } catch (final Exception e) {
      throw e;
    }
  }

}
