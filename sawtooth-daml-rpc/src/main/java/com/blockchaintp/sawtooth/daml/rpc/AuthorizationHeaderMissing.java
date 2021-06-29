package com.blockchaintp.sawtooth.daml.rpc;

import java.io.IOException;

/**
 * Indicates a missing authorisation header
 */
public class AuthorizationHeaderMissing extends IOException {
    public AuthorizationHeaderMissing() {
        super("Authorization header not present");
    }
}
