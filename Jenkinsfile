#!groovy

// Copyright 2017 Intel Corporation
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
// ------------------------------------------------------------------------------


pipeline {
    agent {
        ansiColor('xterm') {
            node {
                label 'worker'
                customWorkspace "workspace/${env.BUILD_TAG}"
            }
        }
    }

    triggers { 
        pollSCM('*/15 * * * *') 
    }

    options {
        ansiColor('xterm')
        timestamps()
        buildDiscarder(logRotator(daysToKeepStr: '31'))
    }

    environment {
        ORGANIZATION="dev.catenasys.com:8083/blockchaintp"
        DOCKER_URL="https://dev.catenasys.com:8083"
        ISOLATION_ID = sh(returnStdout: true, script: 'printf $BUILD_TAG | sha256sum | cut -c1-64').trim()
        COMPOSE_PROJECT_NAME = sh(returnStdout: true, script: 'printf $BUILD_TAG | sha256sum | cut -c1-64').trim()
    }

    stages {

        stage('Fetch Tags') {
            steps {
                sh 'git fetch --tag'
            }
        }

        stage('Build Packages') {
            steps {
                sh 'docker-compose -f docker/docker-compose-build.yaml build'
                sh 'docker-compose -f docker/docker-compose-build.yaml up --abort-on-container-exit'
                sh 'docker-compose -f docker-compose-installed.yaml build'
            }
        }

        stage('Tag and Push Docker images') {
            steps{
                withCredentials([usernamePassword(credentialsId: 'btp-build-nexus', usernameVariable:'DOCKER_USER', passwordVariable:'DOCKER_PASSWORD')]) {
                    sh "docker login -u $DOCKER_USER --password=$DOCKER_PASSWORD $DOCKER_URL"
                    sh '''
                        TAG_VERSION="`git describe --dirty`";
                        for img in `docker images --filter reference="*:$ISOLATION_ID" --format "{{.Repository}}"`; do
                            docker tag $img:$ISOLATION_ID $ORGANIZATION/$img:$TAG_VERSION
                            docker push $ORGANIZATION/$img:$TAG_VERSION
                        done
                    '''

                }
            }
        }

        stage('Create Git Archive') {
            steps {
                sh '''
                    REPO=$(git remote show -n origin | grep Fetch | awk -F'[/.]' '{print $6}')
                    VERSION=`git describe --dirty`
                    git archive HEAD --format=zip -9 --output=$REPO-$VERSION.zip
                    git archive HEAD --format=tgz -9 --output=$REPO-$VERSION.tgz
                '''
            }
        }

    }

    post {
        always {
            sh 'docker-compose -f docker/docker-compose-build.yaml down'
            sh 'docker run -v $PWD:/project/daml-on-sawtooth daml-on-sawtooth-build-local:${ISOLATION_ID}  mvn clean'
	        sh '''
                TAG_VERSION="`git describe --dirty`";
                for img in `docker images --filter reference="*:$ISOLATION_ID" --format "{{.Repository}}"`; do
                    docker tag $img:$ISOLATION_ID $img:$TAG_VERSION
                    docker rmi -f $img
                done
            '''
        }
        success {
            archiveArtifacts '*.tgz, *.zip'
        }
        aborted {
            error "Aborted, exiting now"
        }
        failure {
            error "Failed, exiting now"
        }
    }
}
