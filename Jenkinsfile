pipeline {
    agent any

    tools {
        maven 'Maven-3.9'
    }

    environment {
        DOCKERHUB_USERNAME = credentials('dockerhub-credentials')
        DOCKER_IMAGE_PREFIX = "${DOCKERHUB_USERNAME}/clouddelivery"
        IMAGE_TAG = "${env.GIT_COMMIT[0..6]}"
        APP_SERVER_IP = "${env.APP_SERVER_IP ?: ''}"
        SOURCE_DIR = "Source Code"
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 45, unit: 'MINUTES')
        disableConcurrentBuilds()
        timestamps()
    }

    triggers {
        githubPush()
    }

    stages {

        // ─────────────────────────────────────────────────────────────────────
        // STAGE 1 — Checkout
        // ─────────────────────────────────────────────────────────────────────
        stage('Checkout') {
            steps {
                checkout scm
                script {
                    env.IMAGE_TAG = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
                    echo "Building commit: ${env.IMAGE_TAG} on branch: ${env.BRANCH_NAME}"
                }
            }
        }

        // ─────────────────────────────────────────────────────────────────────
        // STAGE 2 — Unit Tests (parallel)
        // ─────────────────────────────────────────────────────────────────────
        stage('Unit Tests') {
            parallel {
                stage('Auth Tests') {
                    steps {
                        dir("${SOURCE_DIR}/authentication-service") {
                            sh 'mvn test -B --no-transfer-progress'
                        }
                    }
                    post {
                        always {
                            junit testResults: "${SOURCE_DIR}/authentication-service/target/surefire-reports/*.xml",
                                  allowEmptyResults: true
                        }
                    }
                }
                stage('Notification Tests') {
                    steps {
                        dir("${SOURCE_DIR}/notification-service") {
                            sh 'mvn test -B --no-transfer-progress'
                        }
                    }
                    post {
                        always {
                            junit testResults: "${SOURCE_DIR}/notification-service/target/surefire-reports/*.xml",
                                  allowEmptyResults: true
                        }
                    }
                }
                stage('Order Tests') {
                    steps {
                        dir("${SOURCE_DIR}/order-service") {
                            sh 'mvn test -B --no-transfer-progress'
                        }
                    }
                    post {
                        always {
                            junit testResults: "${SOURCE_DIR}/order-service/target/surefire-reports/*.xml",
                                  allowEmptyResults: true
                        }
                    }
                }
                stage('Delivery Tests') {
                    steps {
                        dir("${SOURCE_DIR}/delivery-service") {
                            sh 'mvn test -B --no-transfer-progress'
                        }
                    }
                    post {
                        always {
                            junit testResults: "${SOURCE_DIR}/delivery-service/target/surefire-reports/*.xml",
                                  allowEmptyResults: true
                        }
                    }
                }
            }
        }

        // ─────────────────────────────────────────────────────────────────────
        // STAGE 3 — Build Docker Images (parallel)
        // ─────────────────────────────────────────────────────────────────────
        stage('Build Docker Images') {
            parallel {
                stage('Build Auth') {
                    steps {
                        dir("${SOURCE_DIR}/authentication-service") {
                            sh """
                                docker build \
                                    -t ${DOCKER_IMAGE_PREFIX}-auth:${IMAGE_TAG} \
                                    -t ${DOCKER_IMAGE_PREFIX}-auth:latest \
                                    .
                            """
                        }
                    }
                }
                stage('Build Notification') {
                    steps {
                        dir("${SOURCE_DIR}/notification-service") {
                            sh """
                                docker build \
                                    -t ${DOCKER_IMAGE_PREFIX}-notification:${IMAGE_TAG} \
                                    -t ${DOCKER_IMAGE_PREFIX}-notification:latest \
                                    .
                            """
                        }
                    }
                }
                stage('Build Order') {
                    steps {
                        dir("${SOURCE_DIR}/order-service") {
                            sh """
                                docker build \
                                    -t ${DOCKER_IMAGE_PREFIX}-order:${IMAGE_TAG} \
                                    -t ${DOCKER_IMAGE_PREFIX}-order:latest \
                                    .
                            """
                        }
                    }
                }
                stage('Build Delivery') {
                    steps {
                        dir("${SOURCE_DIR}/delivery-service") {
                            sh """
                                docker build \
                                    -t ${DOCKER_IMAGE_PREFIX}-delivery:${IMAGE_TAG} \
                                    -t ${DOCKER_IMAGE_PREFIX}-delivery:latest \
                                    .
                            """
                        }
                    }
                }
                stage('Build Gateway') {
                    steps {
                        dir("${SOURCE_DIR}/api-gateway") {
                            sh """
                                docker build \
                                    -t ${DOCKER_IMAGE_PREFIX}-gateway:${IMAGE_TAG} \
                                    -t ${DOCKER_IMAGE_PREFIX}-gateway:latest \
                                    .
                            """
                        }
                    }
                }
            }
        }

        // ─────────────────────────────────────────────────────────────────────
        // STAGE 4 — Security Scan (Trivy)
        // ─────────────────────────────────────────────────────────────────────
        stage('Security Scan') {
            steps {
                script {
                    def services = ['auth', 'notification', 'order', 'delivery', 'gateway']
                    services.each { svc ->
                        sh """
                            docker run --rm \
                                -v /var/run/docker.sock:/var/run/docker.sock \
                                -v \$HOME/.cache/trivy:/root/.cache/trivy \
                                aquasec/trivy:latest image \
                                --exit-code 1 \
                                --severity CRITICAL \
                                --ignore-unfixed \
                                --no-progress \
                                ${DOCKER_IMAGE_PREFIX}-${svc}:${IMAGE_TAG} || true
                        """
                    }
                }
            }
        }

        // ─────────────────────────────────────────────────────────────────────
        // STAGE 5 — Push to Docker Hub
        // ─────────────────────────────────────────────────────────────────────
        stage('Push to Docker Hub') {
            when {
                anyOf {
                    branch 'develop'
                    branch 'main'
                }
            }
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'dockerhub-credentials',
                    usernameVariable: 'DOCKER_USER',
                    passwordVariable: 'DOCKER_PASS'
                )]) {
                    sh 'echo $DOCKER_PASS | docker login -u $DOCKER_USER --password-stdin'
                    script {
                        def services = ['auth', 'notification', 'order', 'delivery', 'gateway']
                        services.each { svc ->
                            sh """
                                docker push ${DOCKER_IMAGE_PREFIX}-${svc}:${IMAGE_TAG}
                                docker push ${DOCKER_IMAGE_PREFIX}-${svc}:latest
                            """
                        }
                    }
                }
            }
        }

        // ─────────────────────────────────────────────────────────────────────
        // STAGE 6 — Deploy to App Server
        // ─────────────────────────────────────────────────────────────────────
        stage('Deploy') {
            when {
                anyOf {
                    branch 'develop'
                    branch 'main'
                }
            }
            steps {
                withCredentials([
                    sshUserPrivateKey(
                        credentialsId: 'app-server-ssh',
                        keyFileVariable: 'SSH_KEY'
                    ),
                    string(credentialsId: 'mail-username', variable: 'MAIL_USERNAME'),
                    string(credentialsId: 'mail-password', variable: 'MAIL_PASSWORD'),
                    usernamePassword(
                        credentialsId: 'dockerhub-credentials',
                        usernameVariable: 'DOCKER_USER',
                        passwordVariable: 'DOCKER_PASS'
                    )
                ]) {
                    sh """
                        ssh -i \$SSH_KEY \
                            -o StrictHostKeyChecking=no \
                            ubuntu@\${APP_SERVER_IP} '
                                set -e
                                cd /opt/clouddelivery/Source\\ Code

                                # Pull latest images
                                echo \$DOCKER_PASS | docker login -u \$DOCKER_USER --password-stdin

                                export IMAGE_TAG=${IMAGE_TAG}
                                export DOCKERHUB_USERNAME=${DOCKER_IMAGE_PREFIX}
                                export MAIL_USERNAME=${MAIL_USERNAME}
                                export MAIL_PASSWORD=${MAIL_PASSWORD}

                                docker compose -f docker-compose.prod.yml pull
                                docker compose -f docker-compose.prod.yml up -d --remove-orphans

                                docker image prune -f
                            '
                    """
                }
            }
        }

        // ─────────────────────────────────────────────────────────────────────
        // STAGE 7 — Smoke Test
        // ─────────────────────────────────────────────────────────────────────
        stage('Smoke Test') {
            when {
                anyOf {
                    branch 'develop'
                    branch 'main'
                }
            }
            steps {
                script {
                    retry(3) {
                        sleep(time: 20, unit: 'SECONDS')
                        sh """
                            curl -sf --max-time 10 \
                                http://\${APP_SERVER_IP}:8080/actuator/health \
                                | grep -q '"status":"UP"'
                        """
                    }
                }
                echo "Smoke test passed — API Gateway is healthy"
            }
        }
    }

    post {
        always {
            sh 'docker logout || true'
            cleanWs()
        }
        success {
            echo "Pipeline succeeded — ${env.IMAGE_TAG} deployed"
        }
        failure {
            echo "Pipeline failed — check logs above"
        }
    }
}
