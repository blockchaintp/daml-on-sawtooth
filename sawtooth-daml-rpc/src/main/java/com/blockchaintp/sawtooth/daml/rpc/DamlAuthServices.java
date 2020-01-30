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

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Logger;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.digitalasset.daml.lf.data.Ref;
import com.digitalasset.ledger.api.auth.AuthService;
import com.digitalasset.ledger.api.auth.AuthServiceJWTPayload;
import com.digitalasset.ledger.api.auth.Claim;
import com.digitalasset.ledger.api.auth.ClaimActAsParty$;
import com.digitalasset.ledger.api.auth.ClaimAdmin$;
import com.digitalasset.ledger.api.auth.ClaimPublic$;
import com.digitalasset.ledger.api.auth.Claims;

import org.json.JSONArray;
import org.json.JSONObject;
import org.spongycastle.util.Arrays;

import io.grpc.Metadata;
import scala.collection.immutable.List;
import scala.collection.immutable.List$;
import scala.collection.mutable.ListBuffer;

import sawtooth.sdk.signing.Secp256k1PublicKey;

/**
 * Responsible for decoding JWTToken sent from GRPC.
 *
 */
public final class DamlAuthServices implements AuthService {

  private static final Logger LOGGER = Logger.getLogger(SawtoothReadService.class.getName());

  private Algorithm ecdsaAlgorithm = null;

  /**
   * @param pubKeyInHex  public key in hexadecimal format
   */
  public DamlAuthServices(final String pubKeyInHex) {
    final Secp256k1PublicKey pubKey = Secp256k1PublicKey.fromHex(pubKeyInHex);
    final byte[] pubKeyBytes = pubKey.getBytes();
    final int midPoint = pubKeyBytes.length / 2;
    final byte[] pubKeyX = Arrays.copyOfRange(pubKeyBytes, 0, midPoint);
    final byte[] pubKeyY = Arrays.copyOfRange(pubKeyBytes, midPoint, pubKeyBytes.length);
    try {
      final AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
      parameters.init(new ECGenParameterSpec("secp256r1"));
      final ECParameterSpec ecParameterSpec = parameters.getParameterSpec(ECParameterSpec.class);

      final ECPublicKeySpec ecPublicKeySpec = new ECPublicKeySpec(
          new ECPoint(new BigInteger(1, pubKeyX), new BigInteger(1, pubKeyY)), ecParameterSpec);

      final KeyFactory kf = KeyFactory.getInstance("EC");
      final ECPublicKey ecPublicKey = (ECPublicKey) kf.generatePublic(ecPublicKeySpec);
      this.ecdsaAlgorithm = Algorithm.ECDSA256(ecPublicKey, null);
    } catch (NoSuchAlgorithmException | InvalidParameterSpecException | InvalidKeySpecException e) {
      LOGGER.info("Unable to obtain public key data type");
      throw new RuntimeException("Unable to construct DamlAuthServices", e);
    }

  }

  /**
   * @param headers grpc metadata
   * @return CompletionStage of Claims
   */
  public CompletionStage<Claims> decodeMetadata(final io.grpc.Metadata headers) {
    try {
      return CompletableFuture.completedFuture(decodeAndParse(headers));
    } catch (final Exception e) {
      return CompletableFuture.completedFuture(Claims.empty());
    }
  }

  private com.digitalasset.ledger.api.auth.Claims decodeAndParse(final io.grpc.Metadata headers) throws Exception {

    final String regex = "Bearer (.*)";
    final Pattern pattern = Pattern.compile(regex);

    final Metadata.Key<String> authorizationKey = Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);
    final String authKeyString = headers.get(authorizationKey);
    final Matcher matcher = pattern.matcher(authKeyString);
    final String tokenString = matcher.group(1);
    if (tokenString == null) {
      throw new Exception();
    }

    final JWTVerifier verifier = JWT.require(this.ecdsaAlgorithm).build();
    final DecodedJWT decodedJWT = verifier.verify(tokenString);
    final AuthServiceJWTPayload jwtPayload = parsePayload(decodedJWT);
    return payloadToDAClaims(jwtPayload);
  }

  private AuthServiceJWTPayload parsePayload(final DecodedJWT decodedJWT) {
    final String payloadBase64String = decodedJWT.getPayload();
    final byte[] payloadInByteArray = Base64.getDecoder().decode(payloadBase64String);
    final JSONObject payloadInJsonObject = new JSONObject(new String(payloadInByteArray));

    final scala.Option<String> ledgerID = scala.Option.apply(payloadInJsonObject.optString("ledgerId"));
    final scala.Option<String> participantID = scala.Option.apply(payloadInJsonObject.optString("participantId"));
    final scala.Option<String> applicationID = scala.Option.apply(payloadInJsonObject.optString("applicationId"));

    final scala.Option<Instant> exp = scala.Option.apply(Instant.ofEpochMilli(payloadInJsonObject.optInt("exp")));
    final Boolean admin = payloadInJsonObject.optBoolean("admin");

    final JSONArray actASInJSONArray = payloadInJsonObject.optJSONArray("actAs");
    final List<String> actAS = List$.MODULE$.empty();
    if (actASInJSONArray != null) {
      for (int index = 0; index < actASInJSONArray.length(); index++) {
        actAS.$colon$colon(actASInJSONArray.getString(index));
      }
    }

    final JSONArray readASInJSONArray = payloadInJsonObject.optJSONArray("readAs");
    final List<String> readAS = List$.MODULE$.empty();
    if (readASInJSONArray != null) {
      for (int index = 0; index < readASInJSONArray.length(); index++) {
        readAS.$colon$colon(readASInJSONArray.getString(index));
      }
    }

    final AuthServiceJWTPayload authServiceJWTPayload = new AuthServiceJWTPayload(ledgerID, participantID,
        applicationID, exp, admin, actAS, readAS);

    return authServiceJWTPayload;
  }

  private Claims payloadToDAClaims(final AuthServiceJWTPayload payload) {

    final ListBuffer<Claim> claimsList = new ListBuffer<Claim>();

    claimsList.$plus$eq(ClaimPublic$.MODULE$);

    if (payload.admin()) {
      claimsList.$plus$eq(ClaimAdmin$.MODULE$);
    }

    payload.actAs().foreach(name -> ClaimActAsParty$.MODULE$.apply(Ref.Party().assertFromString(name)));

    final Claims claims = new Claims(claimsList.toList(), payload.ledgerId(), payload.participantId(), payload.exp());
    return claims;

  }
}
