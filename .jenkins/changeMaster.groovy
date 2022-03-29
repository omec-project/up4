// SPDX-FileCopyrightText: 2022-present Open Networking Foundation <info@opennetworking.org>
// SPDX-License-Identifier: Apache-2.0

// To validate: ./jflint.sh changeMaster.groovy
// This pipeline, checks if the stable environment uses local built images
// if so, tries to run tests with the latest master SHA. If tests are successful
// push a commit to the updateStableEnv branch and opens a PR towards master branch.

// sha1 used for PRs, while commitHash is populated for postmerge
def commitHash = params.commitHash ? params.commitHash : params.sha1
def skipOtherStages = false

pipeline {

  agent {
    label "${params.buildNode}"
  }
  options {
    timeout(time: 120, unit: 'MINUTES')
  }

  environment {
    JAVA_HOME = "/usr/lib/jvm/java-11-amazon-corretto"
    GITHUB_URL = "ssh://git@github.com/omec-project/up4.git"
  }

  stages {
    stage("Environment Cleanup") {
      steps {
        step([$class: "WsCleanup"])
        script {
          if (!params.ghprbPullId?.trim()) {
            // Set PENDING build status
            setBuildStatus("Change master SHA pending", "PENDING", "${env.GITHUB_URL}", "${env.JOB_NAME}")
          }
        }
      }
    }
    stage("Checkout") {
      steps {
        checkout([
                $class           : "GitSCM",
                userRemoteConfigs: [[url          : "${env.GITHUB_URL}",
                                     refspec      : "+refs/heads/*:refs/remotes/origin/* +refs/pull/*:refs/remotes/origin/pr/*",
                                     credentialsId: "github-onf-bot-ssh-key",]],
                branches         : [[name: "${commitHash}"]],
                extensions       : [
                        [$class: "RelativeTargetDirectory", relativeTargetDir: "up4"],
                        [$class: "SubmoduleOption", recursiveSubmodules: true, parentCredentials: true]]
        ],)
      }
    }
    stage("Prep") {
      steps {
        // Docker login
        withCredentials([[$class          : "UsernamePasswordMultiBinding",
                          credentialsId   : "registry.aetherproject.org",
                          usernameVariable: "USERNAME",
                          passwordVariable: "PASSWORD"]]) {
          sh 'docker login registry.aetherproject.org -u $USERNAME -p $PASSWORD'
        }
        // Set JDK 11
        sh "sudo update-java-alternatives -s java-11-amazon-corretto"
      }
    }
    stage("Check if we need to continue") {
      steps {
        dir("${env.WORKSPACE}/up4") {
          script {
            ONOS_IMAGE_STABLE = sh(
                    script: "cat ../.env.stable | grep \'ONOS_IMAGE=\'| sed \'s/ONOS_IMAGE=//\'",
                    returnStdout: true
            ).trim()
            if (ONOS_IMAGE_STABLE == "sdfabric-onos-local:master") {
              skipOtherStages = true
            }
          }
        }
      }
    }
    stage("Prepare env with latest SHA") {
      when {
        expression {
          !skipOtherStages
        }
      }
      steps {
        dir("${env.WORKSPACE}/up4") {
          script {
            SDFABRIC_ONOS_MASTER_SHA = sh(
                    script: 'docker inspect --format=\'{{index .RepoDigests 0}}\' opennetworking/sdfabric-onos:master',
                    returnStdout: true
            ).trim()
            sh sed - i '' -e '/ONOS_VERSION=.*/d' ".env.stable"
            sh sed - i '' -e '/TRELLIS_CONTROL_VERSION=.*/d' ".env.stable"
            sh sed - i '' -e '/UP4_VERSION=.*/d' ".env.stable"
            sh sed - i '' -e '/FABRIC_TNA_VERSION=.*/d' ".env.stable"
            sh sed - i '' -e 's/ONOS_IMAGE=.*/ONOS_IMAGE=${SDFABRIC_ONOS_MASTER_SHA}/' "./.env.stable"
          }
        }
      }
    }
    stage("Dependencies") {
      when {
        expression {
          !skipOtherStages
        }
      }
      options { retry(3) }
      steps {
        dir("${env.WORKSPACE}/up4") {
          sh "make deps"
          sh "cd app && make deps"
          sh "cd scenarios && make deps"
        }
      }
    }
    stage("Build P4 and constants") {
      when {
        expression {
          !skipOtherStages
        }
      }
      steps {
        dir("${env.WORKSPACE}/up4") {
          sh "make build constants"
        }
      }
    }
    stage("Build ONOS app") {
      when {
        expression {
          !skipOtherStages
        }
      }
      steps {
        dir("${env.WORKSPACE}/up4/app") {
          sh "make build-ci"
        }
      }
    }
    stage("Smoke test leaf-spine P4RT") {
      when {
        expression {
          !skipOtherStages
        }
      }
      options { retry(3) }
      steps {
        runSTCSmokeTest("leafspine", "p4rt")
      }
      post {
        always {
          exportArtifactsScenarios("leafspine", "p4rt")
        }
      }
    }
    stage("Smoke test single pair-leaf P4RT") {
      when {
        expression {
          !skipOtherStages
        }
      }
      options { retry(3) }
      steps {
        runSTCSmokeTest("singlepair", "p4rt")
      }
      post {
        always {
          exportArtifactsScenarios("singlepair", "p4rt")
        }
      }
    }
    stage("Smoke test leaf-spine PFCP") {
      when {
        expression {
          !skipOtherStages
        }
      }
      options { retry(3) }
      steps {
        runSTCSmokeTest("leafspine", "pfcp")
      }
      post {
        always {
          exportArtifactsScenarios("leafspine", "pfcp")
        }
      }
    }
    stage("Smoke test single pair-leaf PFCP") {
      when {
        expression {
          !skipOtherStages
        }
      }
      options { retry(3) }
      steps {
        runSTCSmokeTest("singlepair", "pfcp")
      }
      post {
        always {
          exportArtifactsScenarios("singlepair", "pfcp")
        }
      }
    }
    stage("Smoke test single pair-leaf PFCP") {
      when {
        expression {
          !skipOtherStages
        }
      }
      steps {
        // DO COMMIT on updateStableEnv branch and open PR for the current repo status
      }
    }
  }

  post {
    success {
      script {
        if (!params.ghprbPullId?.trim()) {
          setBuildStatus("Change master SHA succeeded", "SUCCESS", "${env.GITHUB_URL}", "${env.JOB_NAME}")
          emailext(
                  subject: "[${env.JOB_NAME}] ${currentBuild.currentResult}: ${params.sha1}",
                  body: "Check results at ${env.BUILD_URL} and merge related PR",
                  to: "daniele@opennetworking.org"
          )
        }
      }
    }
    failure {
      script {
        setBuildStatus("Change master SHA failed", "FAILURE", "${env.GITHUB_URL}", "${env.JOB_NAME}")
        emailext(
                subject: "[${env.JOB_NAME}] ${currentBuild.currentResult}: ${params.sha1}",
                body: "Check results at ${env.BUILD_URL}",
                to: "daniele@opennetworking.org"
        )
      }
    }
  }
}

void runSTCSmokeTest(String topo, String up4_ctrl) {
  dir("${env.WORKSPACE}/up4/scenarios") {
    sh "make reset"
    sh "TOPO=${topo} make ${up4_ctrl}-smoke.xml stcColor=false stcDumpLogs=true"
  }
}

void exportArtifactsScenarios(String topo, String up4_ctrl) {
  dir("${env.WORKSPACE}/up4/scenarios") {
    // Print Docker image version labels, etc.
    sh "make versions > ./tmp/versions.txt"
    // Onos shared folders contain jars and other stuff that is too big and unnecessary,
    // keep just the grpc logs.
    sh "make fix-permissions"
    sh "find ./tmp/onos*/ -type f -not -name \"grpc_*.log\" -print -delete"
    sh "tar -czvf ${topo}-${up4_ctrl}-scenarios-tmp.tar.gz ./tmp"
    archiveArtifacts artifacts: "${topo}-${up4_ctrl}-scenarios-tmp.tar.gz"
  }
}

void setBuildStatus(String message, String state, String url, String context) {
  step([
      $class: "GitHubCommitStatusSetter",
      reposSource: [$class: "ManuallyEnteredRepositorySource", url: url],
      contextSource: [$class: "ManuallyEnteredCommitContextSource", context: context],
      statusResultSource: [ $class: "ConditionalStatusResultSource", results: [[$class: "AnyBuildResult", message: message, state: state]] ]
  ]);
}
