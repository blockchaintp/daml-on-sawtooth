#!groovy

// Copyright © 2023 Paravela Limited
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
  agent { node { label 'worker' } }

  options {
    ansiColor('xterm')
    timestamps()
    buildDiscarder(logRotator(daysToKeepStr: '31'))
    disableConcurrentBuilds()
  }

  environment {
    ISOLATION_ID = sh(returnStdout: true, script: 'echo $BUILD_TAG | sha256sum | cut -c1-64').trim()
    PROJECT_ID = sh(returnStdout: true, script: 'echo $BUILD_TAG | sha256sum | cut -c1-32').trim()
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
        configFileProvider([configFile(fileId: 'global-maven-settings', variable: 'MAVEN_SETTINGS')]) {
          sh '''
            make clean build
          '''
        }
      }
    }

    stage('Package') {
      steps {
        configFileProvider([configFile(fileId: 'global-maven-settings', variable: 'MAVEN_SETTINGS')]) {
          sh '''
            make package
          '''
        }
      }
    }

    stage('Test') {
      steps {
        configFileProvider([configFile(fileId: 'global-maven-settings', variable: 'MAVEN_SETTINGS')]) {
          sh '''
            make test
          '''
        }
        junit '**/target/surefire-reports/*.xml'
        step([$class: "TapPublisher", testResults: "build/daml-test.results"])
      }
    }

    stage("Analyze") {
      steps {
        withSonarQubeEnv('sonarcloud') {
          configFileProvider([configFile(fileId: 'global-maven-settings', variable: 'MAVEN_SETTINGS')]) {
            sh '''
              make analyze
            '''
          }
        }
      }
    }

    stage('Create Archives') {
      steps {
        configFileProvider([configFile(fileId: 'global-maven-settings', variable: 'MAVEN_SETTINGS')]) {
          sh '''
            make archive
          '''
        }
        archiveArtifacts 'build/*.tgz, build/*.zip'
      }
    }

    stage("Publish") {
      when {
        expression { env.BRANCH_NAME == "master" }
      }
      steps {
        configFileProvider([configFile(fileId: 'global-maven-settings', variable: 'MAVEN_SETTINGS')]) {
          sh '''
            make publish
          '''
        }
      }
    }
  }

  post {
      success {
        echo "Successfully completed"
      }
      aborted {
          error "Aborted, exiting now"
      }
      failure {
          error "Failed, exiting now"
      }
  }
}
