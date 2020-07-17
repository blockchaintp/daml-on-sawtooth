// Copyright (c) 2020 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.blockchaintp.sawtooth.daml.rpc

final case class ExtraConfig(
    zmqUrl: String,
    keystore: String,
    logLevel: String,
    maxOpsPerBatch: Int,
    maxOutStandingBatches: Int
)

object ExtraConfig {
  val default =
    ExtraConfig(
      zmqUrl = "tcp://localhost:4004",
      keystore = "/etc/daml/keystore",
      logLevel = "info",
      maxOpsPerBatch = "10".toInt,
      maxOutStandingBatches = "2".toInt
    )
}
