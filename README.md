# CloudDelivery — Plateforme Microservices & DevOps

> Projet full-stack **production-ready** : microservices Spring Boot, pipeline CI/CD Jenkins, infrastructure AWS provisionnée par Terraform et configurée par Ansible.

---

## Table des matières

- [Aperçu du projet](#aperçu-du-projet)
- [Stack technique](#stack-technique)
- [Architecture des microservices](#architecture-des-microservices)
- [Services implémentés](#services-implémentés)
- [Infrastructure AWS](#infrastructure-aws)
- [Pipeline CI/CD](#pipeline-cicd)
- [Démarrage rapide — Local](#démarrage-rapide--local)
- [Déploiement AWS](#déploiement-aws)
- [Structure du projet](#structure-du-projet)
- [Documentation](#documentation)

---

## Aperçu du projet

CloudDelivery est une plateforme de gestion de livraisons e-commerce construite en architecture microservices. Elle couvre l'ensemble de la chaîne DevOps :

- **Backend** : 4 microservices Spring Boot indépendants + API Gateway
- **Messaging** : Apache Kafka pour la communication asynchrone entre services
- **Bases de données** : MongoDB (auth, notifications) + PostgreSQL (orders, deliveries)
- **Conteneurisation** : Docker + Docker Compose (dev & prod)
- **Tests** : 80+ tests unitaires et contrôleurs (JUnit 5 + Mockito)
- **Infrastructure** : AWS provisionné par Terraform (VPC, EC2, SG)
- **Configuration** : Ansible (Docker, Jenkins, JCasC)
- **CI/CD** : Jenkins pipeline 7 étapes avec scan de sécurité Trivy

---

## Stack technique

| Couche | Technologies |
|---|---|
| Langage | Java 17 |
| Framework | Spring Boot 4.0, Spring Cloud Gateway 4.2 |
| Messaging | Apache Kafka 7.5 (Confluent) |
| Bases de données | MongoDB 6.0, PostgreSQL 15 |
| Tests | JUnit 5, Mockito, MockMvc |
| Conteneurs | Docker, Docker Compose |
| Infrastructure | Terraform >= 1.6, AWS (EC2, VPC, SG) |
| Configuration | Ansible 2.x, JCasC |
| CI/CD | Jenkins LTS (JDK 17), Trivy, Docker Hub |
| Build | Maven 3.9 |

---

## Architecture des microservices

```
                        ┌──────────────────────────────┐
                        │         Internet              │
                        └──────────────┬───────────────┘
                                       │ :8080
                        ┌──────────────▼───────────────┐
                        │         API Gateway           │
                        │   Spring Cloud Gateway 4.2    │
                        └──┬──────┬──────┬──────┬──────┘
                           │      │      │      │
              /auth   /notifications /orders  /deliveries
                           │      │      │      │
               ┌───────────┘  ┌───┘  ┌──┘  ┌──┘
               ▼              ▼      ▼     ▼
        ┌──────────┐  ┌────────────┐ ┌────────┐ ┌──────────┐
        │   Auth   │  │Notification│ │ Order  │ │ Delivery │
        │ Service  │  │  Service   │ │Service │ │ Service  │
        │  :8081   │  │   :8082    │ │ :8083  │ │  :8084   │
        └────┬─────┘  └─────┬──────┘ └───┬────┘ └────┬─────┘
             │              │             │            │
             ▼              │             ▼            ▼
          MongoDB     ◄─────┤          PostgreSQL   PostgreSQL
          (auth_db)   Kafka │          (cld_main)  (cld_main)
                            ▼
                         MongoDB
                      (notification_db)
```

**Flux Kafka :**
- Auth Service publie `PasswordResetRequestedEvent` → Notification Service consomme et envoie l'email

---

## Services implémentés

### Authentication Service (port 8081)
- Inscription / Connexion avec JWT (access token + refresh token)
- Réinitialisation de mot de passe par email (code temporaire)
- Stockage MongoDB
- Publication d'événements Kafka (`password-reset-requested`)

### Notification Service (port 8082)
- Consommateur Kafka : reçoit les événements et envoie des emails SMTP
- Stockage de l'historique des notifications (MongoDB)
- API REST : liste des notifications par utilisateur

### Order Service (port 8083)
- CRUD commandes avec machine à états : `PENDING → CONFIRMED → DELIVERED / CANCELLED`
- Calcul automatique du total
- Publication d'événements Kafka à chaque transition
- Stockage PostgreSQL

### Delivery Service (port 8084)
- Gestion des livraisons et des livreurs
- Machine à états complète : `PENDING_ASSIGNMENT → ASSIGNED → PICKED_UP → ON_ROAD → DELIVERED / FAILED`
- Disponibilité des livreurs (disponible / indisponible)
- Stockage PostgreSQL

### API Gateway (port 8080)
- Point d'entrée unique pour tous les services
- Routage par préfixe de chemin (`/auth/**`, `/api/v1/orders/**`, etc.)
- Configuration CORS (Angular localhost:4200 / localhost:4300)
- Health check via Spring Actuator

---

## Infrastructure AWS

L'infrastructure est entièrement décrite en code (IaC) et reproductible en une commande.

### Architecture cloud

```
                        AWS — eu-west-3 (Paris)
┌─────────────────────────────────────────────────────────────┐
│  VPC 10.0.0.0/16                                            │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  Public Subnet 10.0.1.0/24                          │   │
│  │                                                      │   │
│  │  ┌──────────────────┐    ┌──────────────────────┐  │   │
│  │  │  Jenkins Server  │    │    App Server         │  │   │
│  │  │  EC2 t3.medium   │    │    EC2 t3.medium      │  │   │
│  │  │  Ubuntu 22.04    │    │    Ubuntu 22.04        │  │   │
│  │  │  30 Go EBS gp3   │    │    40 Go EBS gp3      │  │   │
│  │  │                  │    │                        │  │   │
│  │  │  • Docker        │    │    • Docker            │  │   │
│  │  │  • Jenkins LTS   │    │    • Docker Compose    │  │   │
│  │  │  • JCasC         │    │    • CloudDelivery     │  │   │
│  │  └──────────────────┘    └──────────────────────┘  │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  Internet Gateway → Route Table → Subnet                   │
└─────────────────────────────────────────────────────────────┘
```

### Modules Terraform

| Module | Ressources créées |
|---|---|
| `networking` | VPC, Subnet public, Internet Gateway, Route Table |
| `security` | SG Jenkins (SSH:22 + 8080 restreints à votre IP), SG App (80/443/8080 public) |
| `compute` | 2× EC2 t3.medium Ubuntu 22.04 (AMI dynamique), EBS gp3 chiffré |

### Rôles Ansible

| Rôle | Ce qu'il installe |
|---|---|
| `common` | Packages système, sysctl (performance réseau), swap 2 Go |
| `docker` | Docker CE 26.1 + Docker Compose v2.27 + config daemon (log rotation) |
| `jenkins` | Container Jenkins LTS-JDK17, JCasC (admin, credentials, plugins), 12 plugins |

---

## Pipeline CI/CD

```
git push origin develop
         │
         ▼  (GitHub Webhook)
  ┌──────────────────────────────────────────────────────┐
  │                  JENKINS PIPELINE                     │
  │                                                      │
  │  Stage 1 │ Checkout          Récupère le SHA Git     │
  │  Stage 2 │ Tests ──────────► 4 services en parallèle │
  │  Stage 3 │ Build Images ───► 5 images Docker //      │
  │  Stage 4 │ Trivy Scan  ───► CVE CRITICAL → bloque    │
  │  Stage 5 │ Push Hub    ───► Docker Hub (tag + latest)│
  │  Stage 6 │ Deploy SSH  ───► docker compose up prod   │
  │  Stage 7 │ Smoke Test  ───► curl /actuator/health    │
  └──────────────────────────────────────────────────────┘
```

- **Tests parallèles** : les 4 services testés simultanément (~3 min au lieu de ~12 min)
- **Trivy** : scan de vulnérabilités CVE sur chaque image — bloque le pipeline si CRITICAL détecté
- **Tag immuable** : chaque image est taguée avec le SHA Git court (`auth:a1b2c3d`)
- **Rollback** : possible via `docker compose up` avec un ancien `IMAGE_TAG`

---

## Démarrage rapide — Local

### Prérequis
- Docker Desktop
- Java 17 + Maven 3.9 (pour les builds locaux)

### Lancer tous les services

```bash
cd "Source Code"
docker compose up -d
```

Les services démarrent dans l'ordre grâce aux `healthcheck` :
1. PostgreSQL + MongoDB + Zookeeper
2. Kafka (attend Zookeeper)
3. Microservices (attendent Kafka + DB)
4. API Gateway (attend tous les microservices)

### Vérifier que tout tourne

```bash
curl http://localhost:8080/actuator/health
# → {"status":"UP"}

curl http://localhost:8080/auth/api/v1/health     # Auth
curl http://localhost:8080/api/v1/orders          # Orders
curl http://localhost:8080/api/v1/deliveries      # Deliveries
curl http://localhost:8080/api/v1/notifications   # Notifications
```

### Lancer les tests

```bash
# Depuis chaque service
cd "Source Code/authentication-service" && mvn test
cd "Source Code/notification-service"  && mvn test
cd "Source Code/order-service"         && mvn test
cd "Source Code/delivery-service"      && mvn test
```

### Variables d'environnement pour les emails (optionnel)

```bash
export MAIL_USERNAME=votre@email.com
export MAIL_PASSWORD=votre_mot_de_passe_app
docker compose up -d notification-service
```

---

## Déploiement AWS

### Prérequis
- AWS CLI configuré (`aws configure`)
- Terraform >= 1.6 installé
- Ansible installé (`pip install ansible`)
- Key Pair créée dans la console AWS (EC2 → Key Pairs)

### 1. Configurer Terraform

```bash
cd infra/terraform
cp terraform.tfvars.example terraform.tfvars
# Éditer terraform.tfvars :
#   key_name    = "clouddelivery-key"
#   your_ip_cidr = "$(curl -s https://checkip.amazonaws.com)/32"
```

### 2. Déployer en une commande

```bash
./scripts/deploy.sh
```

Ce script enchaîne automatiquement :
- `terraform apply` — crée VPC, SG, 2 EC2
- `generate-inventory.sh` — renseigne les IPs dans Ansible
- `ansible-playbook site.yml` — installe Docker + Jenkins + clone le repo

### 3. Accéder à Jenkins

```
http://<jenkins_public_ip>:8080
```

L'URL est affichée dans les outputs Terraform à la fin du déploiement.

### 4. Détruire l'infrastructure (arrêter les frais)

```bash
./scripts/destroy.sh
# Demande confirmation → tape "yes"
```

**Coût estimé :** ~0.40€ pour une session de 4h (2× t3.medium)

---

## Structure du projet

```
CloudDelivery-FullDevopsProject/
│
├── Jenkinsfile                      ← Pipeline CI/CD (7 stages)
├── DEPLOYMENT.md                    ← Rapport architecture enterprise
├── INFRA-EXPLAINED.md               ← Guide détaillé Terraform & Ansible
│
├── scripts/
│   ├── deploy.sh                    ← Terraform + Ansible en une commande
│   ├── destroy.sh                   ← Suppression infrastructure AWS
│   └── generate-inventory.sh        ← Terraform outputs → inventory Ansible
│
├── infra/
│   ├── terraform/
│   │   ├── providers.tf             ← Provider AWS
│   │   ├── variables.tf             ← Paramètres (région, type EC2, IP...)
│   │   ├── main.tf                  ← Orchestration des modules
│   │   ├── outputs.tf               ← IPs publiques après apply
│   │   ├── terraform.tfvars.example ← Template de configuration
│   │   └── modules/
│   │       ├── networking/          ← VPC, Subnet, IGW, Route Table
│   │       ├── security/            ← Security Groups
│   │       └── compute/             ← EC2 Jenkins + App Server
│   │
│   └── ansible/
│       ├── ansible.cfg              ← Config SSH, timeout, parallélisme
│       ├── site.yml                 ← Playbook principal
│       ├── inventory.ini            ← Serveurs cibles (généré auto)
│       ├── group_vars/all.yml       ← Variables globales
│       └── roles/
│           ├── common/              ← Packages système + optimisations
│           ├── docker/              ← Docker CE + Compose v2
│           └── jenkins/             ← Jenkins container + JCasC + plugins
│
└── Source Code/
    ├── docker-compose.yaml          ← Développement local (build sources)
    ├── docker-compose.prod.yml      ← Production (images Docker Hub)
    ├── authentication-service/      ← Spring Boot — JWT, MongoDB, Kafka
    ├── notification-service/        ← Spring Boot — Email, MongoDB, Kafka
    ├── order-service/               ← Spring Boot — PostgreSQL, Kafka
    ├── delivery-service/            ← Spring Boot — PostgreSQL, Kafka
    └── api-gateway/                 ← Spring Cloud Gateway
```

---

## Documentation

Toute la documentation est dans le dossier [`docs/`](./docs/) :

| Document | Description |
|---|---|
| [`docs/MISE-EN-PLACE.md`](./docs/MISE-EN-PLACE.md) | Guide complet d'installation et de lancement (local + AWS) |
| [`docs/JENKINS-EXPLAINED.md`](./docs/JENKINS-EXPLAINED.md) | Jenkins expliqué de A à Z — CI/CD, Jenkinsfile, JCasC |
| [`docs/INFRA-EXPLAINED.md`](./docs/INFRA-EXPLAINED.md) | Terraform & Ansible expliqués de A à Z — chaque fichier justifié |
| [`docs/DEPLOYMENT.md`](./docs/DEPLOYMENT.md) | Rapport architecture enterprise — sécurité, Kubernetes, Sealed Secrets |

---

*Projet portfolio — Yassine Glioual — Java / Spring Boot / DevOps*
