// Copyright (c) 2019 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.blockchaintp.sawtooth.daml.rpc

import java.io.File

import com.digitalasset.platform.index.config.Config
import com.digitalasset.platform.index.config.StartupMode
import com.daml.ledger.participant.state.v1.ParticipantId
import com.digitalasset.api.util.TimeProvider
import com.digitalasset.daml.lf.data.Ref.LedgerString
import com.digitalasset.ledger.api.tls.TlsConfiguration

final case class SawtoothDamlRpcConfig(
    port: Int,
    connect: String,
    archiveFiles: List[File],
    maxInboundMessageSize: Int,
    jdbcUrl: String,
    keystore: String,
    participantId: ParticipantId,
    tlsConfig: Option[TlsConfiguration],
) {
  def makePlatformConfig(): Config = {
    return new Config(
      this.port,
      None,
      List.empty,
      this.maxInboundMessageSize,
      TimeProvider.UTC, // TODO this can't be right
      this.jdbcUrl,
      this.tlsConfig,
      this.participantId,
      Vector.empty,
      StartupMode.MigrateAndStart
    )
  }

}

object SawtoothDamlRpcConfig {
  val DefaultMaxInboundMessageSize = 4194304
  def default: SawtoothDamlRpcConfig =
    new SawtoothDamlRpcConfig(
      0,
      "tcp://localhost:4004",
      List.empty,
      DefaultMaxInboundMessageSize,
      "",
      "/etc/daml/keystore",
      LedgerString.assertFromString("unknown-participant"),
      None
    )
}
