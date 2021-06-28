package com.blockchaintp.sawtooth.daml.rpc;

import java.io.IOException;

public class AuthorizationHeaderMissing extends IOException {

    public AuthorizationHeaderMissing(String message) {
        super(message);
    }
}
