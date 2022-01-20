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
      m2dir = "${HOME}/.m2-audit"
    }
    agent any

    tools {
        // Install the Maven version 3.8.4 and add it to the path.
        maven 'maven384'
    }

    stages {
        stage('Purge Local') {
            steps {
                sh "echo 'Building tag ${tagname}' > build.current.txt"
                sh "date >> build.current.txt"
                sh "echo '' >> build.current.txt"
                sh "echo 'Purge ${m2dir}: ${remove_local_m2}'"
                script {
                    if (remove_local_m2.toBoolean()) {
                        sh "rm -rf ${m2dir}"
                    }
                }
            }
        }
        stage('Build Core') {
            steps {
                dir('mrt-core2') {
                  git branch: "${env.BRANCH_CORE}", url: 'https://github.com/CDLUC3/mrt-core2.git'
                  sh "git remote get-url origin >> ../build.current.txt"
                  sh "git branch >> ../build.current.txt"
                  sh "git log --pretty=full -n 1 >> ../build.current.txt"
                  sh "mvn -Dmaven.repo.local=${m2dir} -s ${MAVEN_HOME}/conf/settings.xml clean install -DskipTests"
                }
            }
        }
        stage('Build Cloud') {
            steps {
                dir('mrt-cloud') {
                  git branch: "${env.BRANCH_CLOUD}", url: 'https://github.com/CDLUC3/mrt-cloud.git'
                  sh "git remote get-url origin >> ../build.current.txt"
                  sh "git branch >> ../build.current.txt"
                  sh "git log --pretty=full -n 1 >> ../build.current.txt"
                  sh "mvn -Dmaven.repo.local=${m2dir} -s ${MAVEN_HOME}/conf/settings.xml clean install -DskipTests"
                }
            }
        }
        stage('Build Audit') {
            steps {
                dir('mrt-audit'){
                  git branch: 'master', url: 'https://github.com/CDLUC3/mrt-audit.git'
                  checkout([
                        $class: 'GitSCM',
                        branches: [[name: "refs/tags/${tagname}"]],
                  ])
                  sh "git remote get-url origin >> ../build.current.txt"
                  sh "git branch >> ../build.current.txt"
                  sh "git log --pretty=medium -n 1 >> ../build.current.txt"
                  sh "mvn -Dmaven.repo.local=${m2dir} -s ${MAVEN_HOME}/conf/settings.xml clean install"
                }
            }
        }

        stage('Archive Resources') { // for display purposes
            steps {
                script {
                  sh "cp build.current.txt ${tagname}"
                  archiveArtifacts artifacts: "${tagname}, build.current.txt, mrt-audit/mrt-auditwarpub/target/mrt-auditwarpub-1.0-SNAPSHOT.war", onlyIfSuccessful: true
                } 
            }
        }
    }
}