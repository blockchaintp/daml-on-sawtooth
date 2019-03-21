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

The DA SDK version 100.11.17 or greater, the Scala SBT build tool and a modern JVM. All of the commands in this README are assumed to be executed from within the repository root.

### Building the example ledger-api-server

```
sbt compile
```

### Compiling the example "quickstart-java" DAML project

We first need to use the DAML SDK to compile some example DAML code into a DAR (DAML archive) file:

```
(cd quickstart-java; da compile)
```

### Running the example ledger-api-server

In order to run the ledger-api-server, we (currently) need to provide it with the DAML packages up front. Assuming we have compiled the DAML code using the step above, we can now issue the following command to start a server:


```
sbt "runMain com.digitalasset.ledger.example.Main quickstart-java/target/quickstart-java.dar"
```

### Running the semantics tests against a ledger-api-server

The quickstart DAR package contains testing scenarios that we can replay against the server that we started in the previous step. To start the tester with the quickstart DAR, issue the following command:

```
sbt "test:runMain com.digitalasset.platform.semantictest.StandaloneSemanticTestRunner quickstart-java/target/quickstart-java.dar"
```

This is the expected output:
TODO
