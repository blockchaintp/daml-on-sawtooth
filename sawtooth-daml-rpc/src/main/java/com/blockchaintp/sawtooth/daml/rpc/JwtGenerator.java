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
import java.io.FileNotFoundException;
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

import akka.protobuf.ByteString;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class JwtGenerator {

  private ECPrivateKey privateKey;
  private JSONObject jsonObject;

  public JwtGenerator(final String privKeyFilename, JSONObject jsonObject) {
    this.jsonObject = jsonObject;
    try {
      extractKeys(privKeyFilename);
    } catch (final Exception e) {
      this.privateKey = null;
    }
  }

  public boolean noPrivateKey() {
    return (this.privateKey != null) ? true : false;
  }

  public String generateToken() {
    Algorithm ecdsa512Algorithm = Algorithm.ECDSA256(null, this.privateKey);
    
    JSONArray jsonaActAs = (JSONArray) this.jsonObject.get("actAs");
    String[] actAs = new String[jsonaActAs.size()];
    int index = 0;
    for (Object value : actAs){
        actAs[index] = (String) value;
        index++;
    }

    JSONArray jsonReadAs = (JSONArray) this.jsonObject.get("readAs");
    String[] readAs = new String[jsonReadAs.size()];
    index = 0;
    for (Object value : readAs){
        readAs[index] = (String) value;
        index++;
    }

    String ledgerId = (String) this.jsonObject.get("ledgerId");
    String participantId = (String) this.jsonObject.get("participantId");
    String applicationId = (String) this.jsonObject.get("applicationId");
    Long exp = (Long) this.jsonObject.get("exp");

    String token = JWT.create()
        .withClaim("ledgerId", ledgerId)
        .withClaim("participantId", participantId)
        .withClaim("applicationId", applicationId)
        .withClaim("exp", exp)
        .withArrayClaim("actAs", actAs) 
        .withArrayClaim("readAs", readAs) 
        .sign(ecdsa512Algorithm);

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

  public static void main(String[] args){

    String pathToPrivateKey = null;
    String pathToPayloadSpec = null;

    if (args.length != 4) {
        System.out.println("Usage: jwtgenerator.sh -pk <path to private key> -claim <claim spec in json>");
        System.exit(1);
    }

    if (args[0].equals("-pk")) {
        pathToPrivateKey = args[1];
    }

    if (args[2].equals("-claim")){
        pathToPayloadSpec = args[3];
    }

    if (pathToPayloadSpec == null){
        System.exit(1);
    }

    File jsonFile = new File(pathToPayloadSpec);
    JSONParser jsonParser = new JSONParser();
    
    try {
        FileReader reader = new FileReader(jsonFile);
        JSONObject jsonObj = (JSONObject) jsonParser.parse(reader);
        JwtGenerator jwtGenerator = new JwtGenerator(pathToPrivateKey, jsonObj);
        
        System.out.println(jwtGenerator.generateToken());

    }catch (FileNotFoundException e){
        System.exit(1);
    } catch(IOException e){
        System.exit(1);
    }catch(ParseException e){
        System.exit(1);
    }

  }

}
