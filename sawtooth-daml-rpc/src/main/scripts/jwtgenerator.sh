#!/bin/sh
# Copyright 2019 Blockchain Techology Partners
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

OPT__PK="$1"
OPT__PK_VALUE="$2"
OPT__CLAIM="$3"
OPT__CLAIM_VALUE="$4"

java -cp sawtooth-daml-rpc-*.jar com.blockchaintp.sawtooth.daml.rpc.JwtGenerator $OPT__PK $OPT__PK_VALUE $OPT__CLAIM $OPT__CLAIM_VALUE
