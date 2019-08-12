#!groovy

// Copyright 2019 Blockchain Technology Partners
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
        checkout([$class: 'GitSCM', branches: [[name: "${GIT_BRANCH}"]],
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
        sh 'docker-compose -f docker/docker-compose-build.yaml build'
        configFileProvider([configFile(fileId: 'global-maven-settings', variable: 'MAVEN_SETTINGS')]) {
          sh 'docker run --rm -v $HOME/.m2/repository:/root/.m2/repository -v $MAVEN_SETTINGS:/root/.m2/settings.xml -v `pwd`:/project/daml-on-sawtooth daml-on-sawtooth-build-local:${ISOLATION_ID} mvn -B clean compile'
          sh 'docker run --rm -v $HOME/.m2/repository:/root/.m2/repository -v $MAVEN_SETTINGS:/root/.m2/settings.xml daml-on-sawtooth-build-local:${ISOLATION_ID} chown -R $UID:$GROUPS /root/.m2/repository'
          sh 'docker run --rm -v `pwd`:/project/daml-on-sawtooth daml-on-sawtooth-build-local:${ISOLATION_ID} find /project -type d -name target -exec chown -R $UID:$GROUPS {} \\;'
          sh 'mkdir -p test-dars && docker run --rm -v `pwd`/test-dars:/out ledger-api-testtool:${ISOLATION_ID} bash -c "java -jar ledger-api-test-tool_2.12.jar -x && cp *.dar /out"'
        }
        sh 'docker-compose -f docker/daml-test.yaml build'
      }
    }

    stage('Test') {
      steps {
        configFileProvider([configFile(fileId: 'global-maven-settings', variable: 'MAVEN_SETTINGS')]) {
          sh 'docker run --rm -v $HOME/.m2/repository:/root/.m2/repository -v $MAVEN_SETTINGS:/root/.m2/settings.xml -v `pwd`:/project/daml-on-sawtooth daml-on-sawtooth-build-local:${ISOLATION_ID} mvn -B verify test'
          sh 'docker run --rm -v $HOME/.m2/repository:/root/.m2/repository -v $MAVEN_SETTINGS:/root/.m2/settings.xml daml-on-sawtooth-build-local:${ISOLATION_ID} chown -R $UID:$GROUPS /root/.m2/repository'
          sh 'docker run --rm -v `pwd`:/project/daml-on-sawtooth daml-on-sawtooth-build-local:${ISOLATION_ID} find /project -type d -name target -exec chown -R $UID:$GROUPS {} \\;'
        }
      }
    }

    stage('Package') {
      steps {
        configFileProvider([configFile(fileId: 'global-maven-settings', variable: 'MAVEN_SETTINGS')]) {
          sh 'docker run --rm -v $HOME/.m2/repository:/root/.m2/repository -v $MAVEN_SETTINGS:/root/.m2/settings.xml -v `pwd`:/project/daml-on-sawtooth daml-on-sawtooth-build-local:${ISOLATION_ID} mvn -B package'
          sh 'docker run --rm -v $HOME/.m2/repository:/root/.m2/repository -v $MAVEN_SETTINGS:/root/.m2/settings.xml daml-on-sawtooth-build-local:${ISOLATION_ID} chown -R $UID:$GROUPS /root/.m2/repository'
          sh 'docker run --rm -v `pwd`:/project/daml-on-sawtooth daml-on-sawtooth-build-local:${ISOLATION_ID} find /project -type d -name target -exec chown -R $UID:$GROUPS {} \\;'
        }
        sh 'docker-compose -f docker-compose-installed.yaml build'
      }
    }

    stage('Integration Test') {
      steps {
        sh 'docker-compose -f docker/daml-test.yaml up --exit-code-from ledger-api-testtool'
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
        sh 'docker-compose -f docker/daml-test.yaml down'
        sh 'docker run -v $PWD:/project/daml-on-sawtooth daml-on-sawtooth-build-local:${ISOLATION_ID} mvn -B clean'
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
