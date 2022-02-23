@Library('merritt-build-library')
import org.cdlib.mrt.build.BuildFunctions;

pipeline {
    /*
     * Params:
     *   tagname
     *   purge_local_m2
     */
    environment {      
      //Branch/tag names to incorporate into the build.  Create one var for each repo.
      BRANCH_CORE = 'java-refactor'
      BRANCH_CLOUD = 'java-refactor'

      //working vars
      M2DIR = "${HOME}/.m2-audit"
      DEF_BRANCH = "master"
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
                  init_build();
                }
            }
        }
        stage('Build Core') {
            steps {
                dir('mrt-core2') {
                  script {
                    build_core();
                    //build_library('https://github.com/CDLUC3/mrt-core2.git', env.BRANCH_CORE, '-DskipTests')
                  }
                }
            }
        }
        stage('Build Cloud') {
            steps {
                dir('mrt-cloud') {
                  git branch: "${env.BRANCH_CLOUD}", url: 'https://github.com/CDLUC3/mrt-cloud.git'
                  sh "git remote get-url origin >> ../build.current.txt"
                  sh "git symbolic-ref -q --short HEAD >> ../build.current.txt || git describe --tags --exact-match >> ../build.current.txt"
                  sh "git log --pretty=full -n 1 >> ../build.current.txt"
                  sh "mvn -Dmaven.repo.local=${m2dir} -s ${MAVEN_HOME}/conf/settings.xml clean install -DskipTests"
                }
            }
        }
        stage('Build Audit') {
            steps {
                dir('mrt-audit'){
                  git branch: "${env.defbranch}", url: 'https://github.com/CDLUC3/mrt-audit.git'
                  sh "git remote get-url origin >> ../build.current.txt"
                  script {
                    if (params.containsKey("branch")) {
                      checkout([
                        $class: 'GitSCM',
                        branches: [[name: "${branch}"]],
                      ])
                      sh "git symbolic-ref -q --short HEAD >> ../build.current.txt || git describe --tags >> ../build.current.txt"
                    } else if (params.containsKey("tagname")) {
                      checkout([
                        $class: 'GitSCM',
                        branches: [[name: "refs/tags/${tagname}"]],
                      ])
                      sh "git symbolic-ref -q --short HEAD >> ../build.current.txt || git describe --tags --exact-match >> ../build.current.txt"
                    }
                  }
                  sh "git log --pretty=medium -n 1 >> ../build.current.txt"
                  sh "mvn -Dmaven.repo.local=${m2dir} -s ${MAVEN_HOME}/conf/settings.xml clean install"
                }
            }
        }

        stage('Archive Resources') { // for display purposes
            steps {
                script {
                  if (params.containsKey("branch")) {
                    def tbranch = branch.replaceFirst(/origin\//, '')
                    sh "cp build.current.txt ${tbranch}"
                    sh "mkdir -p WEB-INF"
                    sh "cp build.current.txt WEB-INF"
                    sh "cp mrt-audit/audit-war/target/mrt-auditwarpub-1.0-SNAPSHOT.war mrt-audit-${tbranch}.war"
                    sh "jar uf mrt-audit-${tbranch}.war WEB-INF/build.current.txt"
                    archiveArtifacts \
                      artifacts: "${tbranch}, build.current.txt, mrt-audit-${tbranch}.war"
                      onlyIfSuccessful: true
                  } else {
                    sh "cp build.current.txt ${tagname}"
                    sh "mkdir -p WEB-INF"
                    sh "cp build.current.txt WEB-INF"
                    sh "cp mrt-audit/audit-war/target/mrt-auditwarpub-1.0-SNAPSHOT.war mrt-audit-${tagname}.war"
                    sh "jar uf mrt-audit-${tagname}.war WEB-INF/build.current.txt"
                    sh "cp mrt-audit-${tagname}.war ${JENKINS_HOME}/userContent"
                    archiveArtifacts \
                      artifacts: "${tagname}, build.current.txt, mrt-audit-${tagname}.war"
                      onlyIfSuccessful: true
                  }

                }
            }
        }
    }
}
