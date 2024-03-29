@Library('merritt-build-library')
import org.cdlib.mrt.build.BuildFunctions;

// See https://github.com/CDLUC3/mrt-jenkins/blob/main/src/org/cdlib/mrt/build/BuildFunctions.groovy

pipeline {
    /*
     * Params:
     *   tagname
     *   purge_local_m2
     */
    environment {      
      //Branch/tag names to incorporate into the build.  Create one var for each repo.
      BRANCH_CORE = 'main'
      BRANCH_CLOUD = 'main'

      //working vars
      M2DIR = "${HOME}/.m2-audit"
      DEF_BRANCH = "main"
    }
    agent any

    tools {
        // Install the Maven version 3.8.4 and add it to the path.
        maven 'maven384'
    }

    stages {
        stage('Purge Local') {
            steps {
                script {
                  new BuildFunctions().init_build();
                }
            }
        }
        stage('Build Core') {
            steps {
                dir('mrt-core2') {
                  script {
                    new BuildFunctions().build_core_library(
                      'https://github.com/CDLUC3/mrt-core2.git', 
                      env.BRANCH_CORE, 
                      '-DskipTests'
                    )
                  }
                }
            }
        }
        stage('Build Cloud') {
            steps {
                dir('mrt-cloud') {
                  script {
                    new BuildFunctions().build_library(
                      'https://github.com/CDLUC3/mrt-cloud.git', 
                      env.BRANCH_CLOUD, 
                      '-DskipTests'
                    )
                  }
                }
            }
        }
        stage('Build Audit') {
            steps {
                dir('mrt-audit'){
                  script {
                    new BuildFunctions().build_war(
                      'https://github.com/CDLUC3/mrt-audit.git',
                      ''
                    )
                  }
                }
            }
        }

        stage('Archive Resources') { // for display purposes
            steps {
                script {
                  new BuildFunctions().save_artifacts(
                    'mrt-audit/audit-war/target/mrt-auditwarpub-1.0-SNAPSHOT.war',
                    'mrt-audit'
                  )
                }
            }
        }
    }
}