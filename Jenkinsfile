// Tasks 8-12: CI/CD pipeline as code (Windows agent)
// Commit -> build -> test -> package -> Docker image -> push to Docker Hub -> run container -> health check
// Jenkins prerequisites: tools "Maven3" and "JDK17", credentials "dockerhub-creds" (Docker Hub user + access token)
pipeline {
  agent any
  tools { maven 'Maven3'; jdk 'JDK17' }

  parameters {
    choice(name: 'ENV', choices: ['dev', 'test'], description: 'Target environment (Spring profile shown in the page footer)')
    choice(name: 'DEPLOY_MODE', choices: ['docker', 'process', 'none'], description: 'docker = image + container, process = deploy.ps1 on port 9091, none = build only')
    booleanParam(name: 'RUN_UI_TESTS', defaultValue: true, description: 'Run Selenium UI tests')
  }

  environment {
    DOCKER_USER = 'anjali192005'
    IMAGE       = "${DOCKER_USER}/dvmp"
    TAG         = "${BUILD_NUMBER}"
    HOST_PORT   = '8081'
  }

  triggers { pollSCM('H/5 * * * *') }

  stages {
    stage('Checkout') {
      steps {
        deleteDir()
        checkout scm
      }
    }

    stage('Build') {
      steps {
        bat 'if exist target\\NUL (rmdir /s /q target) else (if exist target del /f /q target)'
        bat 'mvn -B compile'
      }
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

    // ---------- Option 1: run the jar as a process (Task 8) ----------
    stage('Deploy (process)') {
      when { expression { (params.DEPLOY_MODE ?: 'docker') == 'process' } }
      steps {
        withEnv(['JENKINS_NODE_COOKIE=dontKillMe']) {
          bat "powershell -NoProfile -ExecutionPolicy Bypass -File scripts\\deploy.ps1 -AppProfile ${params.ENV} -Port 9091"
        }
      }
    }

    // ---------- Option 2: Docker image + container (Task 12) ----------
    stage('Docker Check') {
      when { expression { (params.DEPLOY_MODE ?: 'docker') == 'docker' } }
      steps { bat 'docker version' }
    }

    stage('Docker Build') {
      when { expression { (params.DEPLOY_MODE ?: 'docker') == 'docker' } }
      steps { bat "docker build -t ${IMAGE}:${TAG} -t ${IMAGE}:latest ." }
    }

    stage('Docker Push') {
      when { expression { (params.DEPLOY_MODE ?: 'docker') == 'docker' } }
      steps {
        // private docker config folder so the login does not depend on the Windows credential helper
        withEnv(["DOCKER_CONFIG=${env.WORKSPACE}\\.docker"]) {
          withCredentials([usernamePassword(credentialsId: 'dockerhub-creds', usernameVariable: 'DH_USER', passwordVariable: 'DH_PASS')]) {
            bat 'echo %DH_PASS%| docker login -u %DH_USER% --password-stdin'
          }
          bat "docker push ${IMAGE}:${TAG}"
          bat "docker push ${IMAGE}:latest"
          bat 'docker logout'
        }
      }
    }

    stage('Docker Deploy') {
      when { expression { (params.DEPLOY_MODE ?: 'docker') == 'docker' } }
      steps {
        bat returnStatus: true, script: 'docker rm -f dvmp'
        bat "docker run -d --name dvmp -p ${HOST_PORT}:8080 -e SPRING_PROFILES_ACTIVE=${params.ENV} -v dvmp-data:/app/data -v dvmp-uploads:/app/uploads ${IMAGE}:${TAG}"
      }
    }

    stage('Verify') {
      when { expression { (params.DEPLOY_MODE ?: 'docker') == 'docker' } }
      steps {
        retry(10) {
          sleep time: 8, unit: 'SECONDS'
          bat "curl -sf http://localhost:${HOST_PORT}/actuator/health"
        }
        bat 'docker ps --filter name=dvmp'
      }
    }
  }

  post {
    success { echo "Pipeline OK. Image: ${IMAGE}:${TAG}  Portal: http://localhost:${HOST_PORT} (docker) or http://localhost:9091 (process)" }
    failure { echo 'Pipeline FAILED - deployment was skipped or incomplete' }
    always  { bat returnStatus: true, script: 'docker image prune -f' }
  }
}
