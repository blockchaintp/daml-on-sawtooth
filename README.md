# Welcome to the DAML on Sawtooth repository

See
* [issue #1](https://github.com/DACH-NY/daml-on-sawtooth/issues/1) for the DAML Ledger self-service integration kit roadmap
* [wiki](https://github.com/DACH-NY/daml-on-sawtooth/wiki) for meta
  information about the project

## Quickstart

This project contains an SBT buildable example of a DAML Ledger API server with a
custom in-memory backend. It is intended to serve as a basis for starting with
the DAML on Sawtooth integration.

### Requirements

The Scala SBT build tool and a modern JVM.

### Building the example ledger-api-server

sbt compile

### Running the example ledger-api-server

sbt "runMain com.digitalasset.ledger.example.Main (dar/dalf files)..."

### Running the semantics tests against a ledger-api-server

sbt "test:runMain com.digitalasset.platform.semantictest.StandaloneSemanticTestRunner (dar/dalf files)..."
