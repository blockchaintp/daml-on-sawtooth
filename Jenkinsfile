#!groovy

// Copyright 2017 Blockchain Technology Partners
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
  agent any
  
  triggers {cron('H H * * *')}

  options {
    ansiColor('xterm')
    timestamps()
    buildDiscarder(logRotator(daysToKeepStr: '31'))
  }

  environment {
    ISOLATION_ID = sh(returnStdout: true, script: 'echo $BUILD_TAG | sha256sum | cut -c1-64').trim()
  }

  stages {
    stage('Fetch Tags') {
      steps {
        checkout([$class: 'GitSCM', branches: [[name: "*/${GIT_BRANCH}"]],
            doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [],
            userRemoteConfigs: [[credentialsId: 'github-credentials',noTags:false, url: "${GIT_URL}"]],
            extensions: [
                  [$class: 'CloneOption',
                  shallow: false,
                  noTags: false,
                  timeout: 60]
            ]])
      }
    }

    stage('Build') {
      steps {
        sh 'docker build -t daml-on-sawtooth-build:${ISOLATION_ID} . -f docker/daml-on-sawtooth-build.docker'
        configFileProvider([configFile(fileId: 'global-maven-settings', variable: 'MAVEN_SETTINGS')]) {
          sh 'docker run --rm -v $HOME/.m2/repository:/root/.m2/repository -v $MAVEN_SETTINGS:/root/.m2/settings.xml -v `pwd`:/project/daml-on-sawtooth daml-on-sawtooth-build:${ISOLATION_ID} mvn -B clean compile'
          sh 'docker run --rm -v $HOME/.m2/repository:/root/.m2/repository -v $MAVEN_SETTINGS:/root/.m2/settings.xml daml-on-sawtooth-build:${ISOLATION_ID} chown -R $UID:$GROUPS /root/.m2/repository'
          sh 'docker run --rm -v `pwd`:/project/daml-on-sawtooth daml-on-sawtooth-build:${ISOLATION_ID} find /project -type d -name target -exec chown -R $UID:$GROUPS {} \\;'
        }
      }
    }

    stage('Test') {
      steps {
        configFileProvider([configFile(fileId: 'global-maven-settings', variable: 'MAVEN_SETTINGS')]) {
          sh 'docker run --rm -v $HOME/.m2/repository:/root/.m2/repository -v $MAVEN_SETTINGS:/root/.m2/settings.xml -v `pwd`:/project/daml-on-sawtooth daml-on-sawtooth-build:${ISOLATION_ID} mvn -B test'
          sh 'docker run --rm -v $HOME/.m2/repository:/root/.m2/repository -v $MAVEN_SETTINGS:/root/.m2/settings.xml daml-on-sawtooth-build:${ISOLATION_ID} chown -R $UID:$GROUPS /root/.m2/repository'
          sh 'docker run --rm -v `pwd`:/project/daml-on-sawtooth daml-on-sawtooth-build:${ISOLATION_ID} find /project -type d -name target -exec chown -R $UID:$GROUPS {} \\;'
        }
      }
    }

    stage('Package') {
      steps {
        configFileProvider([configFile(fileId: 'global-maven-settings', variable: 'MAVEN_SETTINGS')]) {
          sh 'docker run --rm -v $HOME/.m2/repository:/root/.m2/repository -v $MAVEN_SETTINGS:/root/.m2/settings.xml -v `pwd`:/project/daml-on-sawtooth daml-on-sawtooth-build:${ISOLATION_ID} mvn -B package'
          sh 'docker run --rm -v $HOME/.m2/repository:/root/.m2/repository -v $MAVEN_SETTINGS:/root/.m2/settings.xml daml-on-sawtooth-build:${ISOLATION_ID} chown -R $UID:$GROUPS /root/.m2/repository'
          sh 'docker run --rm -v `pwd`:/project/daml-on-sawtooth daml-on-sawtooth-build:${ISOLATION_ID} find /project -type d -name target -exec chown -R $UID:$GROUPS {} \\;'
        }
        sh 'docker-compose -f docker-compose-installed.yaml build'
      }
    }

    stage('Create Archives') {
      steps {
        sh '''
            REPO=$(git remote show -n origin | grep Fetch | awk -F'[/.]' '{print $6}')
            VERSION=`git describe --dirty`
            git archive HEAD --format=zip -9 --output=$REPO-$VERSION.zip
            git archive HEAD --format=tgz -9 --output=$REPO-$VERSION.tgz
        '''
        archiveArtifacts artifacts: '**/target/*.zip'
        archiveArtifacts artifacts: '**/target/*.jar'
      }
    }

  }

  post {
      always {
        junit '**/target/surefire-reports/**/*.xml'
        sh 'docker-compose -f docker/docker-compose-build.yaml down'
        sh 'docker run -v $PWD:/project/daml-on-sawtooth daml-on-sawtooth-build:${ISOLATION_ID} mvn clean'
        sh '''
          for img in `docker images --filter reference="*:$ISOLATION_ID" --format "{{.Repository}}"`; do
            docker rmi -f $img:$ISOLATION_ID
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
