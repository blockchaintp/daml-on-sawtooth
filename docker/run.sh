#!/bin/bash

COMMAND=$1

function start(){
    if [ -d ${PWD}/docker/keys ]; then
        rm -rf ${PWD}/docker/keys
    fi
    docker-compose -f ${PWD}/docker/compose/daml-local.yaml up
}

function stop(){
    if [ -d ${PWD}/docker/keys ]; then
        rm -rf ${PWD}/docker/keys
    fi
    docker-compose -f ${PWD}/docker/compose/daml-local.yaml down
}

case "$COMMAND" in
    start)
        start
        ;;
    stop)
        stop
        ;;
esac