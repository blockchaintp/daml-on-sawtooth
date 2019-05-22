#!/bin/bash -x


docker build -t daml-on-sawtooth-build:${ISOLATION_ID} . -f docker/daml-on-sawtooth-build.docker

docker run -it --rm -v $HOME/.m2:/root/.m2 -v `pwd`:/project/daml-on-sawtooth \
    daml-on-sawtooth-build:${ISOLATION_ID} mvn -B clean

docker run -it --rm -v $HOME/.m2:/root/.m2 -v `pwd`:/project/daml-on-sawtooth \
    daml-on-sawtooth-build:${ISOLATION_ID} mvn -B package

docker run -it --rm -v $HOME/.m2:/root/.m2 -v `pwd`:/project/daml-on-sawtooth \
    daml-on-sawtooth-build:${ISOLATION_ID} chown -R $UID:$GROUPS /root/.m2/repository 

docker run -it --rm -v $HOME/.m2:/root/.m2 -v `pwd`:/project/daml-on-sawtooth \
    daml-on-sawtooth-build:${ISOLATION_ID} find /project -type d -name target \
    -exec chown -R $UID:$GROUPS {} \; 

docker-compose -f docker-compose-installed.yaml build
