// Copyright (c) 2019 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.blockchaintp.sawtooth.daml.rpc

import java.io.File

import com.daml.ledger.participant.state.v1.ParticipantId
import com.digitalasset.daml.lf.data.Ref.LedgerString
import com.digitalasset.ledger.api.tls.TlsConfiguration
import com.digitalasset.platform.indexer.IndexerStartupMode

final case class SawtoothDamlRpcConfig(
    port: Int,
    connect: String,
    auth: String,
    archiveFiles: List[File],
    maxInboundMessageSize: Int,
    jdbcUrl: String,
    keystore: String,
    participantId: ParticipantId,
    tlsConfig: Option[TlsConfiguration],
    startupMode: IndexerStartupMode
)

object SawtoothDamlRpcConfig {
  val DefaultMaxInboundMessageSize = 4194304
  def default: SawtoothDamlRpcConfig =
    new SawtoothDamlRpcConfig(
      0,
      "tcp://localhost:4004",
      "wildcard",
      List.empty,
      DefaultMaxInboundMessageSize,
      "",
      "/etc/daml/keystore",
      LedgerString.assertFromString("unknown-participant"),
      None,
      IndexerStartupMode.MigrateAndStart
    )
}
