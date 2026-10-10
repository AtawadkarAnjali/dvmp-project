// Full CI/CD pipeline: checkout -> build -> test -> package -> docker build/push -> deploy
// Jenkins prerequisites: Maven tool "Maven3", JDK tool "JDK17", credentials "dockerhub-creds"
// (username/password), plugins: Git, Pipeline, JUnit, Docker Pipeline. Chrome installed on the agent.
pipeline {
  agent any
  tools { maven 'Maven3'; jdk 'JDK17' }

  parameters {
    choice(name: 'ENV', choices: ['dev', 'test', 'prod'], description: 'Spring profile / target environment')
    choice(name: 'DEPLOY_TARGET', choices: ['docker', 'tomcat', 'none'], description: 'Where to deploy')
    booleanParam(name: 'RUN_UI_TESTS', defaultValue: true, description: 'Run Selenium tests')
    string(name: 'TOMCAT_WEBAPPS', defaultValue: '/opt/tomcat/webapps', description: 'Tomcat webapps folder')
  }

  environment {
    DOCKER_USER = 'your-dockerhub-username'          // <-- change this
    IMAGE       = "${DOCKER_USER}/dvmp"
    TAG         = "${BUILD_NUMBER}"
    HOST_PORT   = '8081'
  }

  triggers { pollSCM('H/5 * * * *') }

  stages {
    stage('Checkout') { steps { checkout scm } }

    stage('Build') { steps { sh 'mvn -B clean compile' } }

    stage('Test') {
      steps { sh "mvn -B test -DskipUi=${!params.RUN_UI_TESTS}" }
      post {
        always {
          junit 'target/surefire-reports/*.xml'
          archiveArtifacts artifacts: 'target/screenshots/*.png', allowEmptyArchive: true
        }
      }
    }

    stage('Package') {
      steps {
        sh 'mvn -B package -DskipTests'
        archiveArtifacts artifacts: 'target/dvmp.jar', fingerprint: true
      }
    }

    stage('Deploy to Tomcat') {
      when { expression { params.DEPLOY_TARGET == 'tomcat' } }
      steps {
        sh 'mvn -B -Pwar clean package -DskipTests'
        sh "cp target/dvmp.war ${params.TOMCAT_WEBAPPS}/dvmp.war"
        echo "Deployed: http://localhost:8080/dvmp/  (env=${params.ENV})"
      }
    }

    stage('Docker Build') {
      when { expression { params.DEPLOY_TARGET == 'docker' } }
      steps {
        sh "docker build -t ${IMAGE}:${TAG} -t ${IMAGE}:latest ."
      }
    }

    stage('Docker Push') {
      when { expression { params.DEPLOY_TARGET == 'docker' } }
      steps {
        withCredentials([usernamePassword(credentialsId: 'dockerhub-creds', usernameVariable: 'U', passwordVariable: 'P')]) {
          sh 'echo $P | docker login -u $U --password-stdin'
          sh "docker push ${IMAGE}:${TAG}"
          sh "docker push ${IMAGE}:latest"
        }
      }
    }

    stage('Docker Deploy') {
      when { expression { params.DEPLOY_TARGET == 'docker' } }
      steps {
        sh '''
          docker rm -f dvmp || true
          docker run -d --name dvmp -p ${HOST_PORT}:8080 \
            -e SPRING_PROFILES_ACTIVE=${ENV} -v dvmp-data:/app/data -v dvmp-uploads:/app/uploads ${IMAGE}:${TAG}
          for i in $(seq 1 20); do
            curl -sf http://localhost:${HOST_PORT}/actuator/health && exit 0
            sleep 3
          done
          echo "Health check failed"; exit 1
        '''
      }
    }
  }

  post {
    success { echo "Pipeline OK -> ${IMAGE}:${TAG}" }
    failure { echo 'Pipeline FAILED - deployment was skipped' }
  }
}
