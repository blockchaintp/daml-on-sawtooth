*DISCLAIMER: This repo is not yet Apache 2.0 despite the LICENSE file(s).*


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

The DA SDK version 0.11.17 or greater (corresponding to library version 100.11.17), the Scala SBT build tool and a modern JVM. All of the commands in this README are assumed to be executed from within the repository root.

### Building the example ledger-api-server

```
$ sbt compile
...
[success] Total time: 5 s, completed Mar 21, 2019, 1:25:03 PM
```

### Compiling the example "quickstart-java" DAML project

We first need to use the DAML SDK to compile some example DAML code into a DAR (DAML archive) file:

```
$ (cd quickstart-java; da compile)
Created .../daml-on-sawtooth/quickstart-java/target/quickstart-java.dar.
```

### Running the example ledger-api-server

In order to run the ledger-api-server, we (currently) need to provide it with the DAML packages up front. Assuming we have compiled the DAML code using the step above, we can now issue the following command to start a server:

```
$ sbt "runMain com.digitalasset.ledger.example.Main quickstart-java/target/quickstart-java.dar"
...
Initialized Ledger API server version 0.0.1 with ledger-id = sandbox-10eccc3e-5f84-4d35-8888-532451dcb21a, port = 6865, dar file = DamlPackageContainer(List(quickstart-java/target/quickstart-java.dar)), time mode = Static, daml-engine = {}
```

### Running the semantics tests against a ledger-api-server

The quickstart DAR package contains testing scenarios that we can replay against the server that we started in the previous step. To start the tester with the quickstart DAR, issue the following command:

```
$ sbt "test:runMain com.digitalasset.platform.semantictest.StandaloneSemanticTestRunner quickstart-java/target/quickstart-java.dar"
...
Testing scenario: Tests.TradeTest:trade_test
Testing scenario: Tests.IouTest:iou_test
Testing scenario: Main:setup
All scenarios completed.
[success] Total time: 12 s, completed 22-Mar-2019 08:37:12
```

### Running the navigator console

An interactive console for issuing ledger api requests can be started using the SDK and the following command:

```
da run navigator -- console localhost 6865
```

More details regarding the navigator console can be found [here](https://docs.daml.com/tools/navigator/console.html):

Note that for those that prefer a Web UI, a navigator server can be started by specifying "server" instead of "console". Once started, this should also open a page in your default browser.
