/*
 * Copyright 2019 Blockchain Technology Partners Licensed under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable
 * law or agreed to in writing, software distributed under the License is distributed on an "AS IS"
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under the License.
 * ------------------------------------------------------------------------------
 */
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
import java.util.regex.Pattern;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.blockchaintp.sawtooth.daml.exceptions.DamlSawtoothRuntimeException;
import com.daml.ledger.api.auth.AuthService;
import com.daml.ledger.api.auth.AuthServiceJWTPayload;
import com.daml.ledger.api.auth.Claim;
import com.daml.ledger.api.auth.ClaimActAsParty$;
import com.daml.ledger.api.auth.ClaimAdmin$;
import com.daml.ledger.api.auth.ClaimPublic$;
import com.daml.ledger.api.auth.Claims;
import com.daml.lf.data.Ref;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.Arrays;

import io.grpc.Metadata;
import io.grpc.Metadata.Key;
import sawtooth.sdk.signing.Secp256k1PublicKey;
import scala.Option;
import scala.collection.immutable.List;
import scala.collection.immutable.List$;
import scala.collection.mutable.ListBuffer;

/**
 * Responsible for decoding JWTToken sent from GRPC.
 *
 */
public final class DamlAuthServices implements AuthService {

  private static final Logger LOGGER = LoggerFactory.getLogger(DamlAuthServices.class.getName());

  private Algorithm ecdsaAlgorithm = null;

  /**
   * @param pubKeyInHex public key in hexadecimal format
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
      throw new DamlSawtoothRuntimeException("Unable to construct DamlAuthServices", e);
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

  private com.daml.ledger.api.auth.Claims decodeAndParse(final io.grpc.Metadata headers)
      throws Exception {

    final var regex = "Bearer (.*)";
    final var pattern = Pattern.compile(regex);

    final Metadata.Key<String> authorizationKey =
        Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);
    final var authKeyString = headers.get(authorizationKey);
    final var matcher = pattern.matcher(authKeyString);
    if (!matcher.matches()) {
      throw new Exception();
    }
    final var tokenString = matcher.group(1);

    final JWTVerifier verifier = JWT.require(this.ecdsaAlgorithm).build();
    final var decodedJWT = verifier.verify(tokenString);
    final AuthServiceJWTPayload jwtPayload = parsePayload(decodedJWT);
    return payloadToDAClaims(jwtPayload);
  }

  private AuthServiceJWTPayload parsePayload(final DecodedJWT decodedJWT) {
    final String payloadBase64String = decodedJWT.getPayload();
    final byte[] payloadInByteArray = Base64.getDecoder().decode(payloadBase64String);
    final JSONObject payloadInJsonObject = new JSONObject(new String(payloadInByteArray));

    final scala.Option<String> ledgerID =
        scala.Option.apply(payloadInJsonObject.optString("ledgerId"));
    final scala.Option<String> participantID =
        scala.Option.apply(payloadInJsonObject.optString("participantId"));
    final scala.Option<String> applicationID =
        scala.Option.apply(payloadInJsonObject.optString("applicationId"));

    final scala.Option<Instant> exp =
        scala.Option.apply(Instant.ofEpochMilli(payloadInJsonObject.optInt("exp")));
    final Boolean admin = payloadInJsonObject.optBoolean("admin");

    final var actASInJSONArray = payloadInJsonObject.optJSONArray("actAs");
    final List<String> actAS = List$.MODULE$.empty();
    if (actASInJSONArray != null) {
      for (var index = 0; index < actASInJSONArray.length(); index++) {
        actAS.$colon$colon(actASInJSONArray.getString(index));
      }
    }

    final var readASInJSONArray = payloadInJsonObject.optJSONArray("readAs");
    final List<String> readAS = List$.MODULE$.empty();
    if (readASInJSONArray != null) {
      for (var index = 0; index < readASInJSONArray.length(); index++) {
        readAS.$colon$colon(readASInJSONArray.getString(index));
      }
    }

    return new AuthServiceJWTPayload(ledgerID,
        participantID, applicationID, exp, admin, actAS, readAS);

  }

  private Claims payloadToDAClaims(final AuthServiceJWTPayload payload) {

    final ListBuffer<Claim> claimsList = new ListBuffer<>();

    claimsList.$plus$eq(ClaimPublic$.MODULE$);

    if (payload.admin()) {
      claimsList.$plus$eq(ClaimAdmin$.MODULE$);
    }

    payload.actAs()
        .foreach(name -> ClaimActAsParty$.MODULE$.apply(Ref.Party().assertFromString(name)));

    return new Claims(claimsList.toList(), payload.ledgerId(), payload.participantId(), Option.empty(), payload.exp());
  }



  @Override
  public Key<String> AUTHORIZATION_KEY() {
    return Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);
  }

  @Override
  public void com$daml$ledger$api$auth$AuthService$_setter_$AUTHORIZATION_KEY_$eq(
      final Key<String> x) {
    // Nasty Scala artifact. believe this is an autogenerated setter for a val in a trait
    // But the naming indicates that it is meant to be some sort of constant.
  }
}
