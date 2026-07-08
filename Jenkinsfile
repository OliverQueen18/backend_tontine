pipeline {

    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    parameters {
        choice(
            name: 'ENV',
            choices: ['prod', 'test', 'dev'],
            description: 'Environnement de déploiement'
        )

        booleanParam(
            name: 'SKIP_TESTS',
            defaultValue: false,
            description: 'Ignorer les tests Maven'
        )

        booleanParam(
            name: 'PUSH_DOCKER',
            defaultValue: true,
            description: 'Pousser l\'image Docker sur le registry'
        )
    }

    environment {
        APP_NAME     = 'backend-tontine '
        DOCKER_IMAGE = 'oliverqueen18/backend-tontine'
        DOCKER_TAG   = "${BUILD_NUMBER}"
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build & Test') {
            steps {
                script {
                    if (params.SKIP_TESTS) {
                        sh 'mvn clean package -DskipTests -B'
                    } else {
                        sh 'mvn clean verify -B'
                    }
                }
            }
        }

        stage('Docker Build') {
            steps {
                sh """
                docker build \
                  -t ${DOCKER_IMAGE}:${DOCKER_TAG} \
                  -t ${DOCKER_IMAGE}:latest \
                  -t ${DOCKER_IMAGE}:${params.ENV} \
                  .
                """
            }
        }

        stage('Docker Push') {
            when {
                expression { params.PUSH_DOCKER }
            }
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'dockerhub-credentials',
                    usernameVariable: 'DOCKER_USER',
                    passwordVariable: 'DOCKER_PASS'
                )]) {
                    sh """
                    echo \$DOCKER_PASS | docker login -u \$DOCKER_USER --password-stdin
                    docker push ${DOCKER_IMAGE}:${DOCKER_TAG}
                    docker push ${DOCKER_IMAGE}:latest
                    docker push ${DOCKER_IMAGE}:${params.ENV}
                    """
                }
            }
        }
    }

    post {
        success {
            echo "Pipeline TONTINE backend reussi (${DOCKER_IMAGE}:${DOCKER_TAG})"
        }
        failure {
            echo 'Pipeline TONTINE backend echoue'
        }
        always {
            cleanWs()
        }
    }
}
