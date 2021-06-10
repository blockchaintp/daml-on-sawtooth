# Building daml-on-besu

## Prerequisites

1. Docker installation <https://docs.docker.com/install/>
1. docker-compose <https://docs.docker.com/compose/install/>
1. a relatively recent `make` tool

The rest of the toolchain is pulled in via the various docker images defined in
the project.

## Steps to build

1. Clone the repository.

  ```shell
  git clone git@github.com:blockchaintp/daml-on-sawtooth.git
  ```

1. if desired

  ```shell
  export ISOLATION_ID=somvalue
  ```

  This will cause the dcker images to be tagged with a version of `somevalue`.
  The default value for `ISOLATION_ID` is `local`

1. `make all`

This will compile, package, and run the integration tests. The following docker
images produce the following docker images:

* `sawtooth-daml-tp:${ISOLATION_ID}`
* `sawtooth-daml-rpc:${ISOLATION_ID}`

## Developing

An unusual item should be noted.  We derive our versions directly from the `git
describe` on the repository which pays attention to annotated tags on the host
repository.  In order to make that work, the `project.version` of the maven
`pom.xml` files are defined as `${revision}`.  Therefore if you choose not to
use the Makefile based build you must provide maven the `revision` property
from the command line, as in the following:

`mvn -Drevision=local compile`
