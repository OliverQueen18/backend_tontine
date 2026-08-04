pipeline {

    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        timeout(time: 45, unit: 'MINUTES')
    }

    parameters {
        choice(
            name: 'ENV',
            choices: ['prod', 'test', 'dev'],
            description: 'Environnement de déploiement'
        )

        // Heap Maven en Mo — baisser à 768 si le serveur plante encore
        string(
            name: 'MAVEN_HEAP_MB',
            defaultValue: '1024',
            description: 'Mémoire max Maven (Mo) pendant docker build. Essayer 768 si OOM.'
        )

        booleanParam(
            name: 'PUSH_DOCKER',
            defaultValue: true,
            description: 'Pousser l\'image Docker sur le registry'
        )
    }

    environment {
        APP_NAME     = 'backend-tontine'
        DOCKER_IMAGE = 'oliverqueen18/backend-tontine'
        DOCKER_TAG   = "${BUILD_NUMBER}"
        MAVEN_OPTS   = "-Xmx${params.MAVEN_HEAP_MB}m -XX:+UseSerialGC -Djava.awt.headless=true"
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        // Un seul build Maven, dans Docker (évite mvn host + mvn image = double RAM)
        stage('Docker Build') {
            steps {
                sh """
                set -e
                echo "Build backend plafonné : Maven heap=${params.MAVEN_HEAP_MB} Mo"
                docker build \
                  --build-arg MAVEN_OPTS="${MAVEN_OPTS}" \
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
                    echo "\$DOCKER_PASS" | docker login -u "\$DOCKER_USER" --password-stdin
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
            echo 'Echec — si OOM/plantage serveur, relancer avec MAVEN_HEAP_MB=768'
        }
        always {
            sh '''
            docker image prune -f || true
            docker builder prune -f --filter until=24h || true
            '''
            cleanWs()
        }
    }
}
