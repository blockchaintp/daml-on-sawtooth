// Copyright (c) 2019 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.blockchaintp.sawtooth.daml.rpc

import java.io.File

import com.digitalasset.ledger.api.tls.TlsConfiguration

final case class Config(
    port: Int,
    connect: String,
    keystore: String,
    tlsConfig: Option[TlsConfiguration]
)

object Config {
  def default: Config =
    new Config(0, "tcp://localhost:4004", "/etc/daml/keystore",None)
}