// Task 8: CI/CD pipeline as code (Windows agent)
// Prerequisites in Jenkins: tools "Maven3" and "JDK17" (Manage Jenkins > Tools)
pipeline {
  agent any
  tools { maven 'Maven3'; jdk 'JDK17' }

  parameters {
    choice(name: 'ENV', choices: ['dev', 'test'], description: 'Target environment (Spring profile shown in the page footer)')
    booleanParam(name: 'RUN_UI_TESTS', defaultValue: true, description: 'Run Selenium UI tests')
    booleanParam(name: 'DEPLOY', defaultValue: true, description: 'Deploy after a successful build')
  }

  triggers { pollSCM('H/5 * * * *') }

  stages {
    stage('Checkout') {
      steps { checkout scm }
    }

    stage('Build') {
      steps { bat 'mvn -B clean compile' }
    }

    stage('Test') {
      steps { bat "mvn -B test -DskipUi=${!params.RUN_UI_TESTS}" }
      post {
        always {
          junit 'target/surefire-reports/*.xml'
          archiveArtifacts artifacts: 'target/screenshots/*.png', allowEmptyArchive: true
        }
      }
    }

    stage('Package') {
      steps {
        bat 'mvn -B package -DskipTests'
        archiveArtifacts artifacts: 'target/dvmp.jar', fingerprint: true
      }
    }

    stage('Deploy') {
      when { expression { params.DEPLOY } }
      steps {
        withEnv(['JENKINS_NODE_COOKIE=dontKillMe']) {
          bat "powershell -NoProfile -ExecutionPolicy Bypass -File scripts\\deploy.ps1 -AppProfile ${params.ENV} -Port 9091"
        }
      }
    }
  }

  post {
    success { echo "Pipeline OK. Portal: http://localhost:9091 (env=${params.ENV})" }
    failure { echo 'Pipeline FAILED - deployment was skipped' }
  }
}
