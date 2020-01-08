// Copyright (c) 2019 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.blockchaintp.sawtooth.daml.rpc

import java.io.File

import com.digitalasset.daml.lf.data.Ref
import com.digitalasset.ledger.api.tls.TlsConfiguration
import scopt.Read

object Cli {

  private implicit val ledgerStringRead: Read[Ref.LedgerString] =
    Read.stringRead.map(Ref.LedgerString.assertFromString)

  private val pemConfig = (path: String, config: SawtoothDamlRpcConfig) =>
    config.copy(
      tlsConfig = config.tlsConfig.fold(
        Some(TlsConfiguration(enabled = true, None, Some(new File(path)), None))
      )(c => Some(c.copy(keyFile = Some(new File(path)))))
    )

  private val crtConfig = (path: String, config: SawtoothDamlRpcConfig) =>
    config.copy(
      tlsConfig = config.tlsConfig.fold(
        Some(TlsConfiguration(enabled = true, Some(new File(path)), None, None))
      )(c => Some(c.copy(keyCertChainFile = Some(new File(path)))))
    )

  private val cacrtConfig = (path: String, config: SawtoothDamlRpcConfig) =>
    config.copy(
      tlsConfig = config.tlsConfig.fold(
        Some(TlsConfiguration(enabled = true, None, None, Some(new File(path))))
      )(c => Some(c.copy(trustCertCollectionFile = Some(new File(path)))))
    )

  private def cmdArgParser(
    binaryName: String,
    description: String,
  ) =
    new scopt.OptionParser[SawtoothDamlRpcConfig](binaryName) {
      head(
        description
      )
      opt[Int]("port")
        .optional()
        .action((p, c) => c.copy(port = p))
        .text("Server port. If not set, a random port is allocated.")
      opt[String]("auth")
        .text("Auth setting. Value off or on")
        .action((v, c) => c.copy(auth = v))
      opt[String]("connect")
        .text("validator connection end point, e.g.tcp://validator:4004")
        .action((v, c) => c.copy(connect = v))
      opt[String]("keys")
        .text("a path where this sawtooth-daml-rpc will store its keys")
        .action((v, c) => c.copy(keystore = v))
      opt[String]("pem")
        .optional()
        .text("TLS: The pem file to be used as the private key.")
        .action(pemConfig)
      opt[String]("crt")
        .optional()
        .text(
          "TLS: The crt file to be used as the cert chain. Required if any other TLS parameters are set."
        )
        .action(crtConfig)
      opt[String]("cacrt")
        .optional()
        .text("TLS: The crt file to be used as the the trusted root CA.")
        .action(cacrtConfig)
      opt[String]("jdbc-url")
        .text(
          "The JDBC URL to the postgres database used for the indexer and the index"
        )
        .action((u, c) => c.copy(jdbcUrl = u))
      opt[Ref.LedgerString]("participant-id")
        .optional()
        .text(
          "The participant id given to all components of a ledger api server"
        )
        .action((p, c) => c.copy(participantId = p))
      arg[File]("<archive>...")
        .optional()
        .unbounded()
        .action((f, c) => c.copy(archiveFiles = f :: c.archiveFiles))
        .text(
          "DAR files to load. Scenarios are ignored. The servers starts with an empty ledger by default, but archives are persistent."
        )
    }

  def parse(
      args: Array[String],
      binaryName: String,
      description: String,
  ): Option[SawtoothDamlRpcConfig] =
    cmdArgParser(binaryName, description)
      .parse(args, SawtoothDamlRpcConfig.default)

}
