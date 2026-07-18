# CloudDelivery — Deployment & CI/CD Report

**Project:** CloudDelivery — SaaS E-Commerce Delivery Platform  
**Author:** Yassine Glioual  
**Stack:** Spring Boot 4.0 · Spring Cloud Gateway · Apache Kafka · PostgreSQL · MongoDB · Docker · Kubernetes · Jenkins · Terraform · Ansible · AWS

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Infrastructure as Code — Terraform (AWS)](#2-infrastructure-as-code--terraform-aws)
3. [Configuration Management — Ansible](#3-configuration-management--ansible)
4. [CI/CD Pipeline — Jenkins](#4-cicd-pipeline--jenkins)
5. [Container Registry — Docker Hub](#5-container-registry--docker-hub)
6. [Kubernetes Deployment](#6-kubernetes-deployment)
7. [Enterprise Security](#7-enterprise-security)
8. [Environment Strategy](#8-environment-strategy)
9. [Secrets Management](#9-secrets-management)
10. [Observability](#10-observability)

---

## 1. Architecture Overview

### 1.1 Global Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                          DEVELOPER WORKSTATION                       │
│                                                                       │
│   git push feature/xxx                                                │
└──────────────────────┬────────────────────────────────────────────── ┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────────────┐
│                        GITHUB (Source Control)                        │
│                                                                       │
│   main ◄── develop ◄── feature/*                                     │
│   Pull Request → Code Review → Merge → Webhook trigger               │
└──────────────────────┬────────────────────────────────────────────── ┘
                        │  Webhook (push event)
                        ▼
┌─────────────────────────────────────────────────────────────────────┐
│                   AWS — JENKINS SERVER (EC2 t3.medium)               │
│                                                                       │
│   Jenkins (Docker)                                                    │
│     Stage 1 : Checkout                                                │
│     Stage 2 : Unit & Integration Tests (mvn test)                    │
│     Stage 3 : Build Docker Images (multi-stage)                      │
│     Stage 4 : Security Scan (Trivy — CVE analysis)                  │
│     Stage 5 : Push to Docker Hub (tagged)                            │
│     Stage 6 : Deploy to Kubernetes (kubectl apply)                   │
│     Stage 7 : Smoke Test (health endpoint check)                     │
└──────────────────────┬────────────────────────────────────────────── ┘
                        │ docker push
                        ▼
┌─────────────────────────────────────────────────────────────────────┐
│                       DOCKER HUB (Container Registry)                │
│                                                                       │
│   glioualyassine/clouddelivery-api-gateway:1.0.0                    │
│   glioualyassine/clouddelivery-auth-service:1.0.0                   │
│   glioualyassine/clouddelivery-order-service:1.0.0                  │
│   glioualyassine/clouddelivery-delivery-service:1.0.0               │
│   glioualyassine/clouddelivery-notification-service:1.0.0           │
└──────────────────────┬────────────────────────────────────────────── ┘
                        │ kubectl apply
                        ▼
┌─────────────────────────────────────────────────────────────────────┐
│               AWS — KUBERNETES CLUSTER (EC2 t3.large)                │
│                                                                       │
│  Namespace: clouddelivery-prod                                        │
│                                                                       │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │                    Ingress (Nginx)  :443 / :80              │    │
│  └──────┬──────────────────────────────────────────────────────┘    │
│          │                                                            │
│  ┌───────▼────────────────────────────────────────────────────┐     │
│  │              api-gateway   (ClusterIP :8080)               │     │
│  └───┬───────────────┬──────────────┬──────────────┬──────────┘     │
│      │               │              │              │                 │
│  ┌───▼───┐      ┌────▼────┐   ┌────▼────┐   ┌────▼────────┐       │
│  │ auth  │      │  order  │   │delivery │   │notification │       │
│  │ :8081 │      │  :8083  │   │ :8084   │   │   :8082     │       │
│  └───────┘      └─────────┘   └─────────┘   └─────────────┘       │
│                                                                       │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐  │
│  │   PostgreSQL      │  │     MongoDB       │  │      Kafka       │  │
│  │   (StatefulSet)   │  │   (StatefulSet)   │  │   (StatefulSet)  │  │
│  └──────────────────┘  └──────────────────┘  └──────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
```

### 1.2 Microservices Summary

| Service | Role | DB | Port | Kafka |
|---|---|---|---|---|
| `api-gateway` | Single entry point, CORS, routing | — | 8080 | — |
| `authentication-service` | JWT, registration, password reset | MongoDB | 8081 | Producer |
| `notification-service` | Email dispatch, notification history | MongoDB | 8082 | Consumer |
| `order-service` | Order lifecycle | PostgreSQL | 8083 | Producer |
| `delivery-service` | Delivery & driver management | PostgreSQL | 8084 | Consumer + Producer |

---

## 2. Infrastructure as Code — Terraform (AWS)

### 2.1 What Terraform Provisions

```
infra/
└── terraform/
    ├── modules/
    │   ├── vpc/          # VPC, subnets, route tables, IGW
    │   ├── ec2/          # EC2 instances, key pairs, user data
    │   ├── security/     # Security groups (inbound/outbound rules)
    │   └── iam/          # Roles and policies (EC2 → ECR, S3)
    └── envs/
        ├── dev/
        │   ├── main.tf
        │   ├── variables.tf
        │   └── terraform.tfvars
        └── prod/
            ├── main.tf
            ├── variables.tf
            └── terraform.tfvars
```

### 2.2 AWS Resources Created

| Resource | Type | Purpose |
|---|---|---|
| VPC | `aws_vpc` | Isolated network `10.0.0.0/16` |
| Public Subnet | `aws_subnet` | Jenkins server, Bastion |
| Private Subnet | `aws_subnet` | Kubernetes nodes, Databases |
| Internet Gateway | `aws_internet_gateway` | Public internet access |
| NAT Gateway | `aws_nat_gateway` | Private subnets → internet (updates) |
| EC2 Jenkins | `aws_instance` t3.medium | CI/CD server |
| EC2 K8s Master | `aws_instance` t3.large | Kubernetes control plane |
| EC2 K8s Workers | `aws_instance` t3.medium ×2 | Kubernetes worker nodes |
| Security Group Jenkins | `aws_security_group` | Ports 22 (Bastion only), 8080 (admin) |
| Security Group K8s | `aws_security_group` | Port 443, inter-node communication |
| IAM Role | `aws_iam_role` | EC2 → read/write Docker Hub credentials from SSM |
| S3 Bucket | `aws_s3_bucket` | Terraform state backend (remote state) |
| DynamoDB Table | `aws_dynamodb_table` | Terraform state locking |

### 2.3 Network Security Architecture

```
Internet
    │
    ▼
[Internet Gateway]
    │
    ▼
[Public Subnet 10.0.1.0/24]
    ├── Bastion Host (SSH entry point)
    └── Jenkins EC2
            │ Private SG only
            ▼
[Private Subnet 10.0.2.0/24]
    ├── K8s Master
    ├── K8s Worker 1
    └── K8s Worker 2
```

**Security Group — Jenkins:**
```hcl
ingress {
  from_port   = 22
  to_port     = 22
  protocol    = "tcp"
  cidr_blocks = [var.bastion_ip]   # SSH only from Bastion
}
ingress {
  from_port   = 8080
  to_port     = 8080
  protocol    = "tcp"
  cidr_blocks = [var.admin_cidr]   # Jenkins UI from admin IP only
}
egress {
  from_port   = 0
  to_port     = 0
  protocol    = "-1"
  cidr_blocks = ["0.0.0.0/0"]     # Outbound unrestricted (Docker Hub, GitHub)
}
```

---

## 3. Configuration Management — Ansible

### 3.1 What Ansible Configures

```
infra/
└── ansible/
    ├── inventory/
    │   ├── dev.ini         # Dev EC2 IPs
    │   └── prod.ini        # Prod EC2 IPs
    ├── group_vars/
    │   ├── all.yml         # Common vars (Java version, Docker version)
    │   ├── jenkins.yml     # Jenkins-specific vars
    │   └── k8s.yml         # Kubernetes-specific vars
    ├── roles/
    │   ├── common/         # Base packages, timezone, swap
    │   ├── docker/         # Docker CE install, daemon.json hardening
    │   ├── jenkins/        # Jenkins install, plugins, JCasC config
    │   └── kubernetes/     # kubeadm init, CNI (Calico), kubectl
    └── playbooks/
        ├── site.yml        # Master playbook
        ├── jenkins.yml     # Jenkins-only playbook
        └── k8s.yml         # Kubernetes-only playbook
```

### 3.2 Docker Hardening via Ansible (`daemon.json`)

```json
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "3"
  },
  "no-new-privileges": true,
  "userns-remap": "default",
  "live-restore": true,
  "icc": false
}
```

| Parameter | Security benefit |
|---|---|
| `no-new-privileges` | Prevents privilege escalation via setuid/setgid |
| `userns-remap` | Containers run as non-root on the host |
| `icc: false` | Disables direct inter-container communication (enforced by Docker network) |
| `live-restore` | Containers survive Docker daemon restart |

### 3.3 Jenkins — Jenkins Configuration as Code (JCasC)

Jenkins is configured entirely via YAML (no manual click-ops), reproducible and version-controlled:

```yaml
# jenkins.yml (JCasC)
jenkins:
  securityRealm:
    local:
      allowsSignup: false
      users:
        - id: admin
          password: "${JENKINS_ADMIN_PASSWORD}"   # injected from AWS SSM

  authorizationStrategy:
    roleBased:
      roles:
        global:
          - name: admin
            permissions: [Overall/Administer]
          - name: developer
            permissions: [Job/Build, Job/Read, Job/Workspace]

credentials:
  system:
    domainCredentials:
      - credentials:
          - usernamePassword:
              id: docker-hub-credentials
              username: "${DOCKERHUB_USERNAME}"
              password: "${DOCKERHUB_TOKEN}"
          - string:
              id: kubeconfig
              secret: "${KUBECONFIG_BASE64}"
```

---

## 4. CI/CD Pipeline — Jenkins

### 4.1 Branching Strategy (GitFlow)

```
main        ← production releases only (tags: v1.0.0, v1.1.0)
develop     ← integration branch (merges from features)
feature/*   ← individual feature branches
hotfix/*    ← urgent production fixes
```

**Trigger rules:**

| Branch | Jenkins Action |
|---|---|
| `feature/*` | Build + Test only (no push, no deploy) |
| `develop` | Build + Test + Push (tag: `develop-<commit_sha>`) |
| `main` | Build + Test + Push (tag: `<version>`) + Deploy to prod |

### 4.2 Pipeline Stages

```groovy
// Jenkinsfile
pipeline {
    agent any

    environment {
        REGISTRY     = "glioualyassine"
        IMAGE_TAG    = "${env.GIT_COMMIT[0..7]}"
        SERVICES     = "api-gateway authentication-service notification-service order-service delivery-service"
    }

    stages {

        stage('Checkout') { ... }

        stage('Unit & Integration Tests') {
            parallel {
                stage('order-service')        { steps { sh 'mvn test -pl order-service' } }
                stage('delivery-service')     { steps { sh 'mvn test -pl delivery-service' } }
                stage('notification-service') { steps { sh 'mvn test -pl notification-service' } }
                stage('authentication-service') { steps { sh 'mvn test -pl authentication-service' } }
            }
        }

        stage('Build Docker Images') {
            steps {
                script {
                    SERVICES.split().each { svc ->
                        sh "docker build -t ${REGISTRY}/clouddelivery-${svc}:${IMAGE_TAG} ./Source\\ Code/${svc}"
                    }
                }
            }
        }

        stage('Security Scan — Trivy') {
            steps {
                script {
                    SERVICES.split().each { svc ->
                        sh """
                            trivy image \
                              --exit-code 1 \
                              --severity HIGH,CRITICAL \
                              --no-progress \
                              ${REGISTRY}/clouddelivery-${svc}:${IMAGE_TAG}
                        """
                    }
                }
            }
        }

        stage('Push to Docker Hub') {
            when { branch pattern: 'develop|main', comparator: 'REGEXP' }
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'docker-hub-credentials',
                    usernameVariable: 'DOCKER_USER',
                    passwordVariable: 'DOCKER_TOKEN'
                )]) {
                    sh "echo $DOCKER_TOKEN | docker login -u $DOCKER_USER --password-stdin"
                    script {
                        SERVICES.split().each { svc ->
                            sh "docker push ${REGISTRY}/clouddelivery-${svc}:${IMAGE_TAG}"
                        }
                    }
                }
            }
        }

        stage('Deploy to Kubernetes') {
            when { branch 'main' }
            steps {
                withCredentials([string(credentialsId: 'kubeconfig', variable: 'KUBECONFIG_DATA')]) {
                    sh '''
                        echo "$KUBECONFIG_DATA" | base64 -d > /tmp/kubeconfig
                        export KUBECONFIG=/tmp/kubeconfig

                        for svc in $SERVICES; do
                            kubectl set image deployment/${svc} \
                                ${svc}=${REGISTRY}/clouddelivery-${svc}:${IMAGE_TAG} \
                                -n clouddelivery-prod
                        done

                        kubectl rollout status deployment/api-gateway -n clouddelivery-prod --timeout=5m
                    '''
                }
            }
        }

        stage('Smoke Test') {
            when { branch 'main' }
            steps {
                sh '''
                    curl -f --retry 5 --retry-delay 10 \
                        https://api.clouddelivery.local/actuator/health | grep UP
                '''
            }
        }
    }

    post {
        always {
            junit '**/target/surefire-reports/*.xml'
            sh 'docker logout'
            sh 'rm -f /tmp/kubeconfig'
        }
        failure {
            emailext(
                subject: "FAILED: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                body: "Pipeline failed. Check: ${env.BUILD_URL}",
                to: 'yassineag1011@gmail.com'
            )
        }
        success {
            echo "Deployment successful — image tag: ${IMAGE_TAG}"
        }
    }
}
```

### 4.3 Pipeline Flow Diagram

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│   Checkout   │────▶│ Tests (///)  │────▶│ Build Images │
│  (GitHub)    │     │ 4 services   │     │ (Docker)     │
└──────────────┘     └──────────────┘     └──────────────┘
                                                  │
                              ┌───────────────────▼──────────┐
                              │  Security Scan (Trivy)        │
                              │  CVE HIGH/CRITICAL → FAIL    │
                              └───────────────────┬──────────┘
                                                  │  PASS
                              ┌───────────────────▼──────────┐
                              │  Push Docker Hub              │
                              │  tag: <git_sha>              │
                              └───────────────────┬──────────┘
                                                  │  (main only)
                              ┌───────────────────▼──────────┐
                              │  kubectl set image            │
                              │  Rolling update (zero-downtime)│
                              └───────────────────┬──────────┘
                                                  │
                              ┌───────────────────▼──────────┐
                              │  Smoke Test                   │
                              │  GET /actuator/health → UP   │
                              └──────────────────────────────┘
```

---

## 5. Container Registry — Docker Hub

### 5.1 Image Naming Convention

```
glioualyassine/clouddelivery-<service>:<tag>
```

| Tag | When | Example |
|---|---|---|
| `<git_sha>` | Every build on `develop` and `main` | `a3f9c12` |
| `latest` | Every merge to `main` | `latest` |
| `v1.0.0` | Release tag on `main` | `v1.0.0` |
| `develop-<sha>` | Build on `develop` branch | `develop-a3f9c12` |

### 5.2 Multi-Stage Dockerfile Security

All Dockerfiles follow the same hardened multi-stage pattern:

```dockerfile
# Stage 1 — Build (Maven + JDK)
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B          # Layer cache for dependencies
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2 — Runtime (JRE only, Alpine minimal)
FROM eclipse-temurin:17-jre-alpine
RUN apk add --no-cache curl               # For healthchecks only
RUN addgroup -S appgroup && adduser -S appuser -G appgroup   # Non-root user
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
RUN chown appuser:appgroup app.jar
USER appuser                               # Never run as root
EXPOSE 808X
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Security benefits of multi-stage:**
- Final image contains **no Maven, no JDK, no source code** — only the JAR and JRE
- Alpine base: ~50 MB vs ~600 MB for full JDK image — smaller attack surface
- Non-root user: container compromise does not give host root access
- No shell in production path

---

## 6. Kubernetes Deployment

### 6.1 Manifest Structure

```
infra/
└── k8s/
    ├── namespace.yaml
    ├── configmaps/
    │   ├── api-gateway-config.yaml
    │   ├── order-service-config.yaml
    │   └── ...
    ├── secrets/
    │   ├── mongodb-secret.yaml        # base64 encoded, managed by Sealed Secrets
    │   ├── postgres-secret.yaml
    │   └── kafka-secret.yaml
    ├── deployments/
    │   ├── api-gateway.yaml
    │   ├── authentication-service.yaml
    │   ├── notification-service.yaml
    │   ├── order-service.yaml
    │   └── delivery-service.yaml
    ├── services/
    │   ├── api-gateway-svc.yaml       # LoadBalancer (external)
    │   └── *-svc.yaml                 # ClusterIP (internal only)
    ├── ingress/
    │   └── ingress.yaml               # Nginx Ingress + TLS
    └── statefulsets/
        ├── postgres.yaml
        ├── mongodb.yaml
        └── kafka.yaml
```

### 6.2 Deployment Manifest Example (order-service)

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-service
  namespace: clouddelivery-prod
  labels:
    app: order-service
    version: "1.0.0"
spec:
  replicas: 2
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 0          # Zero-downtime deployment
      maxSurge: 1
  selector:
    matchLabels:
      app: order-service
  template:
    metadata:
      labels:
        app: order-service
    spec:
      securityContext:
        runAsNonRoot: true
        runAsUser: 1000
        fsGroup: 1000
      containers:
        - name: order-service
          image: glioualyassine/clouddelivery-order-service:IMAGE_TAG
          ports:
            - containerPort: 8083
          env:
            - name: SPRING_DATASOURCE_URL
              valueFrom:
                secretKeyRef:
                  name: postgres-secret
                  key: jdbc-url
            - name: SPRING_KAFKA_BOOTSTRAP_SERVERS
              value: "kafka:9093"
          resources:
            requests:
              memory: "256Mi"
              cpu: "200m"
            limits:
              memory: "512Mi"
              cpu: "500m"
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8083
            initialDelaySeconds: 30
            periodSeconds: 10
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8083
            initialDelaySeconds: 60
            periodSeconds: 15
          securityContext:
            allowPrivilegeEscalation: false
            readOnlyRootFilesystem: true
            capabilities:
              drop: [ALL]
```

### 6.3 Ingress — TLS Termination

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: clouddelivery-ingress
  namespace: clouddelivery-prod
  annotations:
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
    cert-manager.io/cluster-issuer: "letsencrypt-prod"
spec:
  tls:
    - hosts:
        - api.clouddelivery.local
      secretName: clouddelivery-tls
  rules:
    - host: api.clouddelivery.local
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: api-gateway
                port:
                  number: 8080
```

### 6.4 Kubernetes Network Policies

Only explicitly authorized traffic is allowed between pods:

```yaml
# NetworkPolicy: allow only api-gateway to reach microservices
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-gateway-to-services
  namespace: clouddelivery-prod
spec:
  podSelector:
    matchLabels:
      tier: microservice
  ingress:
    - from:
        - podSelector:
            matchLabels:
              app: api-gateway
      ports:
        - protocol: TCP
          port: 8081
        - protocol: TCP
          port: 8082
        - protocol: TCP
          port: 8083
        - protocol: TCP
          port: 8084
  policyTypes:
    - Ingress
```

---

## 7. Enterprise Security

### 7.1 Security at Each Layer

```
┌──────────────────────────────────────────────────────────────────┐
│ LAYER 1 — Source Code                                             │
│   ✓ No hardcoded secrets (enforced via git-secrets pre-commit hook)│
│   ✓ Branch protection on main (PR required, 1 review minimum)    │
│   ✓ Dependency audit: mvn dependency-check (OWASP)              │
└──────────────────────────────────────────────────────────────────┘
┌──────────────────────────────────────────────────────────────────┐
│ LAYER 2 — CI/CD Pipeline                                          │
│   ✓ Trivy: CVE scan on every built image (HIGH/CRITICAL → fail) │
│   ✓ OWASP Dependency-Check: known vulnerable libraries          │
│   ✓ Credentials stored in Jenkins Credentials Store (encrypted) │
│   ✓ Docker logout after every push (post{} block)               │
│   ✓ kubeconfig deleted from disk after deploy (/tmp ephemeral)  │
│   ✓ Least privilege: Jenkins runs with minimal IAM permissions  │
└──────────────────────────────────────────────────────────────────┘
┌──────────────────────────────────────────────────────────────────┐
│ LAYER 3 — Container                                               │
│   ✓ Non-root user in all Dockerfiles (USER appuser)             │
│   ✓ Read-only root filesystem (readOnlyRootFilesystem: true)    │
│   ✓ No privileged containers (allowPrivilegeEscalation: false)  │
│   ✓ All Linux capabilities dropped (capabilities.drop: [ALL])   │
│   ✓ Minimal Alpine base image (reduced attack surface)           │
│   ✓ Multi-stage build: no build tools in production image       │
└──────────────────────────────────────────────────────────────────┘
┌──────────────────────────────────────────────────────────────────┐
│ LAYER 4 — Kubernetes                                              │
│   ✓ Network Policies: deny all by default, allow per-service    │
│   ✓ RBAC: service accounts with minimum required permissions    │
│   ✓ Pod Security Admission: restricted profile enforced          │
│   ✓ Secrets encrypted at rest (etcd encryption)                 │
│   ✓ Resource limits on all pods (prevents DoS via runaway pods) │
│   ✓ Liveness + Readiness probes on all services                 │
│   ✓ Namespaced isolation (clouddelivery-prod, clouddelivery-dev) │
└──────────────────────────────────────────────────────────────────┘
┌──────────────────────────────────────────────────────────────────┐
│ LAYER 5 — Network / AWS                                           │
│   ✓ VPC: private subnets for K8s nodes and databases            │
│   ✓ Security Groups: least-privilege inbound rules              │
│   ✓ TLS 1.2+ enforced at Ingress (cert-manager + Let's Encrypt) │
│   ✓ All HTTP → HTTPS redirect                                    │
│   ✓ Jenkins accessible from admin IP only (no public exposure)  │
│   ✓ SSH via Bastion host only (no direct EC2 SSH)               │
└──────────────────────────────────────────────────────────────────┘
┌──────────────────────────────────────────────────────────────────┐
│ LAYER 6 — Application                                             │
│   ✓ JWT (HMAC-SHA256): short-lived access token (1h)            │
│   ✓ Refresh token rotation: new refresh token on every refresh  │
│   ✓ Password hashing: BCrypt (cost factor 10)                   │
│   ✓ Password reset: 6-digit code, hashed at rest, TTL 15 min   │
│   ✓ CORS: whitelist only known origins (Angular apps)           │
│   ✓ Input validation: Bean Validation (Jakarta) on all DTOs     │
│   ✓ Kafka: no type headers (prevents deserialization attacks)   │
└──────────────────────────────────────────────────────────────────┘
```

### 7.2 Trivy CVE Scan in Pipeline

```bash
# Fails the pipeline if any HIGH or CRITICAL CVE is found
trivy image \
  --exit-code 1 \
  --severity HIGH,CRITICAL \
  --ignore-unfixed \
  --format json \
  --output trivy-report.json \
  glioualyassine/clouddelivery-order-service:${IMAGE_TAG}
```

### 7.3 OWASP Dependency-Check (Maven)

```xml
<!-- pom.xml — parent or per-service -->
<plugin>
    <groupId>org.owasp</groupId>
    <artifactId>dependency-check-maven</artifactId>
    <version>10.0.4</version>
    <configuration>
        <failBuildOnCVSS>7</failBuildOnCVSS>   <!-- fail on CVSS >= 7 -->
        <format>HTML</format>
    </configuration>
</plugin>
```

---

## 8. Environment Strategy

| Environment | Branch | Image Tag | K8s Namespace | Replicas |
|---|---|---|---|---|
| **Development** | `feature/*` | not pushed | local only | 1 |
| **Staging** | `develop` | `develop-<sha>` | `clouddelivery-staging` | 1 |
| **Production** | `main` | `v1.x.x` | `clouddelivery-prod` | 2+ |

### 8.1 Environment-Specific Config

Each environment uses its own Kubernetes ConfigMap and Secrets. No env-specific logic exists in the application code — all configuration is externalized via environment variables (12-Factor App principle).

```
Dev:       localhost:xxxx (Docker Compose)
Staging:   staging.clouddelivery.local (K8s namespace: staging)
Prod:      api.clouddelivery.local (K8s namespace: prod, replicas: 2)
```

---

## 9. Secrets Management

### 9.1 What is a Secret and Where Does It Live

| Secret | Where stored | How injected |
|---|---|---|
| MongoDB credentials | AWS SSM Parameter Store | Kubernetes Secret via External Secrets Operator |
| PostgreSQL credentials | AWS SSM Parameter Store | Kubernetes Secret |
| JWT signing key | AWS SSM Parameter Store | Kubernetes Secret |
| Kafka credentials | Kubernetes Secret | env var in pod |
| SMTP credentials (Gmail) | AWS SSM Parameter Store | Kubernetes Secret |
| Docker Hub token | Jenkins Credentials Store | `withCredentials` block |
| kubeconfig | Jenkins Credentials Store | `withCredentials` block, deleted after use |

### 9.2 No Hardcoded Secrets — Pre-commit Hook

```bash
# .git/hooks/pre-commit
# Blocks commit if any secret pattern is detected
git-secrets --scan
```

Patterns blocked: `password=`, `secret=`, `api_key=`, AWS keys, JWT secrets.

### 9.3 Kubernetes Secrets — Sealed Secrets

Plain Kubernetes Secrets are base64 only (not encrypted). We use **Bitnami Sealed Secrets** to encrypt secrets before committing to Git:

```bash
# Encrypt a secret for Git storage
kubeseal --format yaml < plain-secret.yaml > sealed-secret.yaml
git add sealed-secret.yaml   # safe to commit — only the cluster can decrypt
```

---

## 10. Observability

### 10.1 Health Endpoints

All services expose Spring Boot Actuator health endpoints:

```
GET /actuator/health           → UP / DOWN (aggregated)
GET /actuator/health/liveness  → liveness probe (K8s)
GET /actuator/health/readiness → readiness probe (K8s)
```

### 10.2 Logging

All services log to stdout (JSON format in production) — Docker and Kubernetes collect and forward logs automatically.

Log levels by environment:
- Development: `DEBUG` for application packages
- Production: `INFO` for application, `WARN` for frameworks

### 10.3 Future: Metrics & Tracing

| Tool | Role |
|---|---|
| Prometheus | Scrape `/actuator/prometheus` from each service |
| Grafana | Dashboards: JVM memory, Kafka consumer lag, HTTP latency |
| Jaeger / Zipkin | Distributed tracing across microservices |
| ELK Stack | Centralized log aggregation (Elasticsearch + Kibana) |

---

## Summary — Implementation Checklist

| Phase | Task | Status |
|---|---|---|
| **Backend** | authentication-service | ✅ Done |
| **Backend** | notification-service | ✅ Done |
| **Backend** | order-service | ✅ Done |
| **Backend** | delivery-service | ✅ Done |
| **Backend** | api-gateway (Spring Cloud Gateway) | ✅ Done |
| **Testing** | Unit tests (80 tests, 0 failures) | ✅ Done |
| **Docker** | Dockerfiles (multi-stage) + docker-compose | ✅ Done |
| **IaC** | Terraform — AWS VPC, EC2, Security Groups | ⏳ Next |
| **Config** | Ansible — Jenkins + Docker + K8s install | ⏳ Next |
| **CI/CD** | Jenkinsfile — 7-stage pipeline | ⏳ Next |
| **K8s** | Manifests — Deployments, Services, Ingress | ⏳ Next |
| **Frontend** | Angular — Customer Portal + Admin Portal | ⏳ Later |
