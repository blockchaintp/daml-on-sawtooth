package com.blockchaintp.sawtooth.daml.rpc;

import java.io.IOException;

/**
 * Indicates a missing authorization header.
 */
public class AuthorizationHeaderMissing extends IOException {
    /**
     * There is no authorization header.
     */
    public AuthorizationHeaderMissing() {
        super("Authorization header not present");
    }
}
