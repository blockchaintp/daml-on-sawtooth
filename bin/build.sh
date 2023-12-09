#!/bin/bash -x
# Copyright © 2023 Paravela Limited
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


docker-compose -f docker/docker-compose-build.yaml build

docker run -it --rm -v $HOME/.m2:/root/.m2 -v "$(pwd):/project/daml-on-sawtooth" \
    daml-on-sawtooth-build-local:${ISOLATION_ID} mvn -B clean

docker run -it --rm -v $HOME/.m2:/root/.m2 -v "$(pwd):/project/daml-on-sawtooth" \
    daml-on-sawtooth-build-local:${ISOLATION_ID} mvn -B package

docker run -it --rm -v $HOME/.m2:/root/.m2 -v "$(pwd):/project/daml-on-sawtooth" \
    daml-on-sawtooth-build-local:${ISOLATION_ID} chown -R $UID:$GROUPS /root/.m2/repository

docker run -it --rm -v $HOME/.m2:/root/.m2 -v "$(pwd):/project/daml-on-sawtooth" \
    daml-on-sawtooth-build-local:${ISOLATION_ID} find /project -type d -name target \
    -exec chown -R $UID:$GROUPS {} \;

docker-compose -f docker-compose-installed.yaml build
