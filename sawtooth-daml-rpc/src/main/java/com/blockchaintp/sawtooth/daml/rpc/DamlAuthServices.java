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

import com.digitalasset.ledger.api.auth.AuthService;
import com.digitalasset.ledger.api.auth.AuthServiceJWTPayload;
import com.digitalasset.ledger.api.auth.Claims;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;

/**
 * Responsible for decoding JWTToken sent from GRPC
 * 
 */
public class DamlAuthServices implements AuthService{

    public final CompletionStage<Claims> decodeMetadata(final io.grpc.Metadata headers) {
        try{
            decodeAndParse(headers);
            // extract token
            // CompletableFuture.completedFuture(payloadToClaims(token))
        } catch (Exception e){
            return null;
        }
        return null;
    }

    private AuthServiceJWTPayload decodeAndParse(final io.grpc.Metadata headers) throws Exception{
        parsePayload("decodedPayload");
        return null;
    }

    private AuthServiceJWTPayload parsePayload(final String jwtPayload){
        return null;
    }

    private Claims payloadToClaims(final AuthServiceJWTPayload payload) {
        return null;
    }
}