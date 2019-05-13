#!/usr/bin/env bash

docker build --build-arg submodule=rpc --build-arg version=0.0.1-SNAPSHOT -t blockchaintp/sawtooth-daml-rpc .

docker build --build-arg submodule=tp --build-arg version=0.0.1-SNAPSHOT -t blockchaintp/sawtooth-daml-tp .
