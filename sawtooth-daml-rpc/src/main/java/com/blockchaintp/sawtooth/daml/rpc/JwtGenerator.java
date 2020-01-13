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
import java.io.FileReader;
import java.io.IOException;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.Security;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPrivateKeySpec;
import java.util.Scanner;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import sawtooth.sdk.signing.Secp256k1PrivateKey;

/**
 * JwtGenerator generates Jwt token for authorisation.
 */
public final class JwtGenerator {
  static final int NUMBER_OF_PARAMS = 4;
  static final int PRIVATE_KEY_POSITION = 0;
  static final int CLAIM_SPEC_POSITION = 2;

  private ECPrivateKey privateKey;
  private JSONObject jsonObject;

    /**
     * Constructor for JwtGenerator.
     * @param privKeyFilename Filename of private key
     * @param payload body of jwt token in json format
     */
  public JwtGenerator(final String privKeyFilename, final JSONObject payload) {
    this.jsonObject = payload;
    try {
      extractKeys(privKeyFilename);
    } catch (final Exception e) {
      this.privateKey = null;
    }
  }

    /**
     * Determine whether privateKey is null.
     * @return boolean indicating whether privateKey is not null
     */
  public boolean noPrivateKey() {
    return this.privateKey != null;
  }

  private String generateToken() {
    Algorithm ecdsaAlgorithm = Algorithm.ECDSA256(null, this.privateKey);

    JSONArray jsonaActAs = (JSONArray) this.jsonObject.get("actAs");
    Object[] objArrayActAs = jsonaActAs.toArray();
    String[] actAs = new String[jsonaActAs.size()];
    int index = 0;
    for (Object value : objArrayActAs) {
        actAs[index] = (String) value;
        index++;
    }

    JSONArray jsonReadAs = (JSONArray) this.jsonObject.get("readAs");
    Object[] objArrayReadAs = jsonReadAs.toArray();
    String[] readAs = new String[jsonReadAs.size()];
    index = 0;
    for (Object value : objArrayReadAs) {
        readAs[index] = (String) value;
        index++;
    }

    String ledgerId = (String) this.jsonObject.get("ledgerId");
    String participantId = (String) this.jsonObject.get("participantId");
    String applicationId = (String) this.jsonObject.get("applicationId");
    Boolean admin = (Boolean) this.jsonObject.get("admin");
    Long exp = (Long) this.jsonObject.get("exp");

    String token = JWT.create()
        .withClaim("ledgerId", ledgerId)
        .withClaim("participantId", participantId)
        .withClaim("applicationId", applicationId)
        .withClaim("admin", admin)
        .withClaim("exp", exp)
        .withArrayClaim("actAs", actAs)
        .withArrayClaim("readAs", readAs)
        .sign(ecdsaAlgorithm);

    return token;
  }

  private void extractKeys(final String privKeyFilename) throws Exception {

    final File privKeyFile = new File(privKeyFilename);

    try {

        // Read private key file and it's content
        final Scanner scanner = new Scanner(privKeyFile);
        final String privKeyString = scanner.nextLine();
        Secp256k1PrivateKey prvKey = Secp256k1PrivateKey.fromHex(privKeyString);
        byte[] prvKeyBytes = prvKey.getBytes();

        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());

        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256k1"));

        ECParameterSpec params = parameters.getParameterSpec(ECParameterSpec.class);

        ECPrivateKeySpec privKeySpec = new ECPrivateKeySpec(new BigInteger(1, prvKeyBytes), params);
        // Obtain key factory
        final KeyFactory keyFactory = KeyFactory.getInstance("EC");

        // Generate Java security competible private and public key types.
        this.privateKey = (ECPrivateKey) keyFactory.generatePrivate(privKeySpec);
        scanner.close();

    } catch (final Exception e) {
      throw e;
    }
  }

  /**
   * Basic main method to generate a JWT compliant token.
   * @param args -pk path to private key -claim claim spec in json
   */
  public static void main(final String[] args) {
    String pathToPrivateKey = null;
    String pathToPayloadSpec = null;

    if (args.length != NUMBER_OF_PARAMS) {
        System.out.println("Usage: -pk <path to private key> -claim <claim spec in json>");
        System.exit(1);
    }

    if (args[PRIVATE_KEY_POSITION].equals("-pk")) {
        pathToPrivateKey = args[PRIVATE_KEY_POSITION + 1];
    }

    if (args[CLAIM_SPEC_POSITION].equals("-claim")) {
      pathToPayloadSpec = args[CLAIM_SPEC_POSITION + 1];
    }

    if (pathToPayloadSpec == null) {
        System.exit(1);
    }

    File jsonFile = new File(pathToPayloadSpec);
    JSONParser jsonParser = new JSONParser();

    try {
        FileReader reader = new FileReader(jsonFile);
        JSONObject jsonObj = (JSONObject) jsonParser.parse(reader);
        JwtGenerator jwtGenerator = new JwtGenerator(pathToPrivateKey, jsonObj);

        System.out.println(jwtGenerator.generateToken());

    } catch (IOException | ParseException e) {
        System.out.print(e);
        System.exit(1);
    }
  }
}
