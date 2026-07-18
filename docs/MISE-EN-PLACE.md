# Guide de Mise en Place — CloudDelivery

> Ce guide explique comment installer, configurer et lancer le projet CloudDelivery de A à Z,
> que ce soit en développement local ou en déploiement AWS complet.

---

## Table des matières

1. [Prérequis](#1-prérequis)
2. [Cloner le projet](#2-cloner-le-projet)
3. [Lancer en développement local](#3-lancer-en-développement-local)
4. [Lancer les tests](#4-lancer-les-tests)
5. [Lancer Jenkins en local](#5-lancer-jenkins-en-local)
6. [Configurer le pipeline Jenkins](#6-configurer-le-pipeline-jenkins)
7. [Déploiement AWS complet](#7-déploiement-aws-complet)
8. [Variables d'environnement](#8-variables-denvironnement)
9. [Ports utilisés](#9-ports-utilisés)
10. [Résolution des problèmes fréquents](#10-résolution-des-problèmes-fréquents)

---

## 1. Prérequis

### Outils obligatoires

| Outil | Version minimale | Vérification | Installation |
|---|---|---|---|
| Java JDK | 17 | `java -version` | [adoptium.net](https://adoptium.net) |
| Maven | 3.9 | `mvn -version` | [maven.apache.org](https://maven.apache.org) |
| Docker Desktop | 25+ | `docker --version` | [docker.com](https://www.docker.com/products/docker-desktop) |
| Git | 2.x | `git --version` | [git-scm.com](https://git-scm.com) |

### Outils pour le déploiement AWS (optionnel)

| Outil | Version minimale | Vérification | Installation |
|---|---|---|---|
| Terraform | 1.6+ | `terraform -version` | [terraform.io](https://developer.hashicorp.com/terraform/install) |
| Ansible | 2.15+ | `ansible --version` | `pip install ansible` |
| AWS CLI | 2.x | `aws --version` | [aws.amazon.com/cli](https://aws.amazon.com/cli/) |

### Comptes nécessaires

| Compte | Utilisation | Gratuit ? |
|---|---|---|
| [GitHub](https://github.com) | Héberger le code source | ✅ Oui |
| [Docker Hub](https://hub.docker.com) | Stocker les images Docker | ✅ Oui (repos publics) |
| [AWS](https://aws.amazon.com) | Héberger Jenkins + App Server | ⚠️ ~0.40€/session de 4h |

### Vérifier que tout est installé

```bash
java -version
# java version "17.x.x"

mvn -version
# Apache Maven 3.9.x

docker --version
# Docker version 26.x.x

git --version
# git version 2.x.x
```

---

## 2. Cloner le projet

```bash
git clone https://github.com/GlioualYassine/CloudDelivery-FullDevopsProject.git
cd CloudDelivery-FullDevopsProject
```

### Structure du projet après clonage

```
CloudDelivery-FullDevopsProject/
├── Jenkinsfile                  ← Pipeline CI/CD
├── README.md                    ← Vue d'ensemble du projet
├── docs/                        ← Toute la documentation
│   ├── MISE-EN-PLACE.md         ← Ce fichier
│   ├── JENKINS-EXPLAINED.md     ← Guide complet Jenkins
│   ├── INFRA-EXPLAINED.md       ← Guide complet Terraform & Ansible
│   └── DEPLOYMENT.md            ← Rapport architecture enterprise
├── jenkins/                     ← Setup Jenkins local
│   ├── Dockerfile
│   ├── docker-compose.yml
│   ├── .env.example
│   └── casc/jenkins.yaml
├── infra/
│   ├── terraform/               ← Infrastructure AWS
│   └── ansible/                 ← Configuration serveurs
├── scripts/
│   ├── deploy.sh                ← Déploiement AWS one-shot
│   ├── destroy.sh               ← Suppression infrastructure
│   └── generate-inventory.sh    ← Génération inventaire Ansible
└── Source Code/
    ├── docker-compose.yaml      ← Dev local
    ├── docker-compose.prod.yml  ← Production (images Docker Hub)
    ├── authentication-service/
    ├── notification-service/
    ├── order-service/
    ├── delivery-service/
    └── api-gateway/
```

---

## 3. Lancer en développement local

### Démarrage complet avec Docker Compose

```bash
# Se placer dans le dossier Source Code
cd "Source Code"

# Démarrer tous les services
docker compose up -d
```

Docker Compose démarre les services dans l'ordre automatiquement grâce aux `healthcheck` :

```
1. postgres + mongodb + zookeeper    (~15s)
          ↓
2. kafka                             (~30s supplémentaires)
          ↓
3. authentication-service            (~60s supplémentaires)
   notification-service
   order-service
   delivery-service
          ↓
4. api-gateway                       (~15s supplémentaires)
```

**Temps total avant que tout soit prêt : environ 2 minutes.**

### Vérifier que tout tourne

```bash
# État de tous les containers
docker compose ps

# Vérifier l'API Gateway (point d'entrée unique)
curl http://localhost:8080/actuator/health
# Réponse attendue : {"status":"UP"}
```

### Tester les endpoints principaux

```bash
# Inscription d'un utilisateur
curl -X POST http://localhost:8080/auth/api/v1/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.com","password":"Test1234!","role":"CLIENT"}'

# Connexion
curl -X POST http://localhost:8080/auth/api/v1/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.com","password":"Test1234!"}'
# Réponse : {"accessToken":"eyJ...","refreshToken":"eyJ..."}

# Créer une commande (avec le token JWT obtenu)
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJ..." \
  -d '{"customerId":"1","items":[{"productId":"p1","quantity":2,"unitPrice":15.0}]}'

# Lister les commandes
curl http://localhost:8080/api/v1/orders \
  -H "Authorization: Bearer eyJ..."
```

### Activer l'envoi d'emails (optionnel)

Par défaut les emails sont désactivés. Pour les activer :

```bash
# Créer le fichier .env dans Source Code/
cat > "Source Code/.env" <<EOF
MAIL_USERNAME=votre@gmail.com
MAIL_PASSWORD=votre_mot_de_passe_application_google
EOF

# Redémarrer le service notification
docker compose up -d notification-service
```

> **Note :** Pour Gmail, le `MAIL_PASSWORD` est un "mot de passe d'application" créé dans :
> Google Account → Sécurité → Validation en 2 étapes → Mots de passe des applications

### Arrêter les services

```bash
# Arrêter sans supprimer les données
docker compose stop

# Arrêter ET supprimer les données (repart à zéro)
docker compose down -v
```

### Voir les logs d'un service

```bash
# Logs en temps réel de l'API Gateway
docker compose logs -f api-gateway

# Logs des 50 dernières lignes de l'auth service
docker compose logs --tail=50 authentication-service
```

---

## 4. Lancer les tests

Les tests s'exécutent sans Docker — ils utilisent des bases de données embarquées (H2, Flapdoodle MongoDB) et un Kafka embarqué.

### Lancer tous les tests (un service à la fois)

```bash
# Authentication Service — 21 tests
cd "Source Code/authentication-service"
mvn test

# Notification Service — 8 tests
cd "Source Code/notification-service"
mvn test

# Order Service — 18 tests
cd "Source Code/order-service"
mvn test

# Delivery Service — 33 tests
cd "Source Code/delivery-service"
mvn test
```

### Résultat attendu pour chaque service

```
[INFO] Tests run: XX, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### Lancer les tests de tous les services en parallèle (bash)

```bash
cd "Source Code"
for svc in authentication-service notification-service order-service delivery-service; do
  (cd $svc && mvn test -B --no-transfer-progress -q && echo "✅ $svc OK" || echo "❌ $svc FAILED") &
done
wait
```

### Rapport de tests

Après `mvn test`, les rapports XML se trouvent dans :
```
Source Code/<service>/target/surefire-reports/*.xml
```

Ces fichiers sont lus par Jenkins pour afficher les graphiques de tests dans l'interface.

---

## 5. Lancer Jenkins en local

Jenkins en local permet de tester le pipeline CI/CD sans AWS, entièrement sur votre machine.

### Étape 1 — Créer un Access Token Docker Hub

1. Aller sur [hub.docker.com](https://hub.docker.com)
2. **Account Settings** → **Security** → **New Access Token**
3. Description : `clouddelivery-jenkins-local`
4. Permission : **Read & Write**
5. Copier le token affiché (il ne sera plus visible après)

### Étape 2 — Créer le fichier `.env`

```bash
cd jenkins
cp .env.example .env
```

Éditer `.env` avec vos valeurs :

```env
JENKINS_ADMIN_PASSWORD=MonMotDePasse123
DOCKERHUB_USERNAME=votre_username_dockerhub
DOCKERHUB_TOKEN=dckr_pat_xxxxxxxxxxxxxxxxxxxx
```

### Étape 3 — Démarrer Jenkins

```bash
# Depuis le dossier jenkins/
docker compose up -d --build
```

Le premier démarrage prend **3 à 5 minutes** (téléchargement de l'image Jenkins + installation des plugins).

### Étape 4 — Accéder à Jenkins

Ouvrir : **http://localhost:8090**

```
Login    : admin
Password : celui défini dans .env → JENKINS_ADMIN_PASSWORD
```

### Vérifier que JCasC a bien fonctionné

Dans Jenkins → **Manage Jenkins** → **System** → vérifier que le message "CloudDelivery CI/CD — Local Jenkins" s'affiche.

Dans Jenkins → **Manage Jenkins** → **Credentials** → vérifier que `dockerhub-credentials` est présent.

Dans Jenkins → **Manage Jenkins** → **Tools** → vérifier que `Maven-3.9` est listé.

### Arrêter Jenkins local

```bash
# Depuis le dossier jenkins/
docker compose stop     # Arrêt (données conservées)
docker compose down     # Arrêt + suppression container (volume conservé)
docker compose down -v  # Arrêt + suppression tout (repart à zéro)
```

---

## 6. Configurer le pipeline Jenkins

### Créer le job Pipeline

1. Jenkins → **New Item**
2. Nom : `clouddelivery-pipeline`
3. Type : **Pipeline**
4. Cliquer **OK**

### Configurer la source

Dans la page de configuration du job :

**Section "Build Triggers" :**
- Cocher **"GitHub hook trigger for GITScm polling"**

**Section "Pipeline" :**
- Definition : **Pipeline script from SCM**
- SCM : **Git**
- Repository URL : `https://github.com/GlioualYassine/CloudDelivery-FullDevopsProject.git`
- Branch Specifier : `*/develop`
- Script Path : `Jenkinsfile`

Cliquer **Save**.

### Lancer le premier build manuellement

Jenkins → `clouddelivery-pipeline` → **Build Now**

Cliquer sur `#1` dans l'historique pour voir les logs en temps réel.

### Configurer le webhook GitHub (déclenchement automatique)

Pour que chaque `git push` déclenche automatiquement Jenkins :

**Sur GitHub :**
1. Repository → **Settings** → **Webhooks** → **Add webhook**
2. Payload URL : `http://<IP_JENKINS>:8090/github-webhook/`
   - En local : utiliser [ngrok](https://ngrok.com) pour exposer le port 8090
   - Sur AWS : utiliser l'IP publique EC2 Jenkins
3. Content type : `application/json`
4. Events : **Just the push event**
5. Cliquer **Add webhook**

> **En local avec ngrok :**
> ```bash
> ngrok http 8090
> # Récupérer l'URL publique : https://xxxx.ngrok.io
> # Webhook URL : https://xxxx.ngrok.io/github-webhook/
> ```

### Ce que le pipeline fait automatiquement

| Stage | Ce qui se passe | Durée |
|---|---|---|
| 1. Checkout | Clone le repo, lit le SHA Git | ~30s |
| 2. Unit Tests | Lance les tests des 4 services en parallèle | ~3 min |
| 3. Build Images | Construit les 5 images Docker en parallèle | ~5 min |
| 4. Security Scan | Trivy analyse les vulnérabilités CVE | ~3 min |
| 5. Push Docker Hub | Pousse les images sur Docker Hub | ~4 min |
| 6. Deploy | SSH vers App Server → docker compose up | ~2 min |
| 7. Smoke Test | Vérifie que l'API Gateway répond | ~1 min |

> **Stages 5, 6, 7** s'exécutent uniquement sur les branches `develop` et `main`.
> Sur une branche `feature/*`, le pipeline s'arrête après le stage 4.

---

## 7. Déploiement AWS complet

Cette section couvre le déploiement de l'infrastructure AWS avec Terraform puis la configuration des serveurs avec Ansible.

### Prérequis AWS

**1. Configurer AWS CLI**
```bash
aws configure
# AWS Access Key ID     : votre_access_key
# AWS Secret Access Key : votre_secret_key
# Default region name   : eu-west-3
# Default output format : json
```

Pour créer des credentials AWS : Console AWS → **IAM** → **Users** → votre utilisateur → **Security credentials** → **Create access key**.

**2. Créer une Key Pair dans AWS**
```
Console AWS → EC2 → Key Pairs → Create key pair
  Nom         : clouddelivery-key
  Type        : RSA
  Format      : .pem
```

Télécharger le fichier `.pem` et le placer dans `~/.ssh/` :
```bash
mv ~/Downloads/clouddelivery-key.pem ~/.ssh/
chmod 400 ~/.ssh/clouddelivery-key.pem
```

**3. Récupérer votre IP publique**
```bash
curl -s https://checkip.amazonaws.com
# Exemple : 86.200.14.53
```

### Configuration Terraform

```bash
cd infra/terraform
cp terraform.tfvars.example terraform.tfvars
```

Éditer `terraform.tfvars` :
```hcl
aws_region        = "eu-west-3"
availability_zone = "eu-west-3a"
key_name          = "clouddelivery-key"
your_ip_cidr      = "86.200.14.53/32"    # Votre IP + /32
instance_type_jenkins = "t3.medium"
instance_type_app     = "t3.medium"
project_name      = "clouddelivery"
environment       = "staging"
```

### Déploiement en une commande

```bash
# Depuis la racine du projet
./scripts/deploy.sh
```

Ce script fait tout automatiquement :
```
1. terraform init    → télécharge le provider AWS
2. terraform apply   → crée VPC + SG + 2 EC2
3. generate-inventory.sh → récupère les IPs et configure Ansible
4. sleep 30s         → attente que les EC2 soient accessibles
5. ansible-playbook  → installe Docker + Jenkins + configure les serveurs
```

Durée totale : **8 à 12 minutes**.

### Résultat après déploiement

```
Outputs:
jenkins_public_ip = "15.188.XX.XX"
app_public_ip     = "54.73.XX.XX"
jenkins_url       = "http://15.188.XX.XX:8080"
app_url           = "http://54.73.XX.XX:8080"

ssh_jenkins = "ssh -i ~/.ssh/clouddelivery-key.pem ubuntu@15.188.XX.XX"
ssh_app     = "ssh -i ~/.ssh/clouddelivery-key.pem ubuntu@54.73.XX.XX"
```

### Configurer Jenkins sur AWS

Après le déploiement, accéder à Jenkins AWS :
```
http://15.188.XX.XX:8080
```

Il faut ajouter les credentials que JCasC n'a pas pu configurer automatiquement (car les valeurs sont personnelles) :

**Jenkins → Manage Jenkins → Credentials → Add Credential :**

| ID | Type | Valeur |
|---|---|---|
| `dockerhub-credentials` | Username + Password | Username Docker Hub + Access Token |
| `app-server-ssh` | SSH Username with private key | `ubuntu` + contenu de `clouddelivery-key.pem` |
| `mail-username` | Secret text | votre email SMTP |
| `mail-password` | Secret text | votre mot de passe application Gmail |

**Puis ajouter l'App Server IP dans les variables globales :**

Jenkins → **Manage Jenkins** → **System** → **Global properties** → **Environment variables** :
- `APP_SERVER_IP` = `54.73.XX.XX` (l'IP de l'App Server)

### Détruire l'infrastructure (arrêter les frais)

```bash
./scripts/destroy.sh
# Taper "yes" pour confirmer
```

**Coût estimé :** ~0.40€ pour une session de 4h sur 2× t3.medium.

---

## 8. Variables d'environnement

### Pour le développement local (`Source Code/.env`)

| Variable | Description | Exemple |
|---|---|---|
| `MAIL_USERNAME` | Adresse email SMTP | `votre@gmail.com` |
| `MAIL_PASSWORD` | Mot de passe d'application Gmail | `xxxx xxxx xxxx xxxx` |

### Pour Jenkins local (`jenkins/.env`)

| Variable | Description | Exemple |
|---|---|---|
| `JENKINS_ADMIN_PASSWORD` | Mot de passe admin Jenkins | `MonMotDePasse123` |
| `DOCKERHUB_USERNAME` | Username Docker Hub | `yassine` |
| `DOCKERHUB_TOKEN` | Access Token Docker Hub | `dckr_pat_xxxx` |

### Pour Terraform (`infra/terraform/terraform.tfvars`)

| Variable | Description | Exemple |
|---|---|---|
| `aws_region` | Région AWS | `eu-west-3` |
| `availability_zone` | Zone de disponibilité | `eu-west-3a` |
| `key_name` | Nom de la Key Pair AWS | `clouddelivery-key` |
| `your_ip_cidr` | Votre IP publique + /32 | `86.200.14.53/32` |
| `instance_type_jenkins` | Type EC2 Jenkins | `t3.medium` |
| `instance_type_app` | Type EC2 App Server | `t3.medium` |

### Credentials Jenkins (à ajouter dans l'interface)

| ID Credential | Type | Description |
|---|---|---|
| `dockerhub-credentials` | Username/Password | Login Docker Hub |
| `app-server-ssh` | SSH Private Key | Clé SSH pour déployer sur l'App Server |
| `mail-username` | Secret text | Email SMTP |
| `mail-password` | Secret text | Mot de passe SMTP |

---

## 9. Ports utilisés

### En développement local

| Service | Port | URL |
|---|---|---|
| API Gateway | 8080 | http://localhost:8080 |
| Auth Service | 8081 | http://localhost:8081 (direct, sans gateway) |
| Notification Service | 8082 | http://localhost:8082 |
| Order Service | 8083 | http://localhost:8083 |
| Delivery Service | 8084 | http://localhost:8084 |
| PostgreSQL | 5432 | localhost:5432 |
| MongoDB | 27017 | localhost:27017 |
| Kafka | 9092 | localhost:9092 (depuis l'hôte) |
| Zookeeper | 2181 | localhost:2181 |
| Jenkins local | 8090 | http://localhost:8090 |

### Sur AWS

| Service | Port | Accès |
|---|---|---|
| Jenkins | 8080 | Votre IP uniquement |
| API Gateway | 8080 | Public |
| SSH Jenkins | 22 | Votre IP uniquement |
| SSH App Server | 22 | Votre IP uniquement |

---

## 10. Résolution des problèmes fréquents

### Les containers ne démarrent pas

```bash
# Voir les logs du container problématique
docker compose logs -f <nom-du-service>

# Exemple
docker compose logs -f kafka
```

**Problème fréquent :** Kafka ne démarre pas si Zookeeper n'est pas encore prêt.
**Solution :** Attendre 30 secondes et relancer `docker compose up -d`.

---

### `mvn test` échoue avec "Connection refused" à MongoDB ou Kafka

Les tests unitaires ne doivent PAS nécessiter de connexion à une vraie base de données — ils utilisent des bases embarquées. Si ce message apparaît, c'est que le profil de test n'est pas activé.

**Solution :**
```bash
mvn test -Dspring.profiles.active=test
```

---

### Jenkins ne trouve pas Maven

**Symptôme :** `mvn: command not found` dans les logs Jenkins.

**Cause :** Maven n'a pas encore été téléchargé par Jenkins (premier build).

**Solution :** Lancer un premier build et attendre que Jenkins télécharge Maven automatiquement via le JCasC `installSource`. Le second build fonctionnera.

---

### `docker build` échoue dans Jenkins

**Symptôme :** `Cannot connect to the Docker daemon`

**Cause :** Le socket Docker n'est pas accessible dans le container Jenkins.

**Vérification :**
```bash
docker exec clouddelivery-jenkins-local docker ps
```

Si ça échoue, vérifier que le volume `/var/run/docker.sock:/var/run/docker.sock` est bien dans le `docker-compose.yml` et que Docker Desktop est démarré.

---

### Terraform échoue avec "No valid credential sources found"

**Cause :** AWS CLI n'est pas configuré.

**Solution :**
```bash
aws configure
# Renseigner Access Key ID, Secret Access Key, région
```

**Vérification :**
```bash
aws sts get-caller-identity
# Doit afficher votre Account ID et ARN
```

---

### SSH refusé vers les EC2 après `terraform apply`

**Cause 1 :** L'EC2 n'a pas encore fini de démarrer.
**Solution :** Attendre 60 secondes et réessayer.

**Cause 2 :** Votre IP a changé depuis la création du Security Group.
**Solution :**
```bash
# Mettre à jour votre IP dans terraform.tfvars
your_ip_cidr = "$(curl -s https://checkip.amazonaws.com)/32"

# Appliquer uniquement le changement
terraform apply -target=module.security
```

---

### Le webhook GitHub ne déclenche pas Jenkins

**Cause 1 :** Jenkins n'est pas accessible depuis internet (Jenkins local sans ngrok).
**Solution :** Utiliser ngrok pour exposer le port 8090.

**Cause 2 :** L'URL du webhook est incorrecte.
**Vérification :** GitHub → Repository → Settings → Webhooks → cliquer sur le webhook → onglet "Recent Deliveries" → voir la réponse.

L'URL correcte se termine par `/github-webhook/` (avec le slash final).

---

## Récapitulatif des commandes essentielles

```bash
# ── DÉVELOPPEMENT LOCAL ──────────────────────────────────────
cd "Source Code"
docker compose up -d                    # Démarrer tous les services
docker compose ps                       # État des services
docker compose logs -f api-gateway      # Logs en temps réel
docker compose down                     # Arrêter

# ── TESTS ────────────────────────────────────────────────────
cd "Source Code/authentication-service" && mvn test
cd "Source Code/notification-service"  && mvn test
cd "Source Code/order-service"         && mvn test
cd "Source Code/delivery-service"      && mvn test

# ── JENKINS LOCAL ────────────────────────────────────────────
cd jenkins
cp .env.example .env && nano .env       # Configurer les secrets
docker compose up -d --build            # Démarrer Jenkins
# → http://localhost:8090
docker compose down                     # Arrêter Jenkins

# ── AWS ──────────────────────────────────────────────────────
cd infra/terraform
cp terraform.tfvars.example terraform.tfvars && nano terraform.tfvars
./scripts/deploy.sh                     # Déployer (Terraform + Ansible)
./scripts/destroy.sh                    # Détruire (arrêter les frais)

# ── UTILES ───────────────────────────────────────────────────
curl https://checkip.amazonaws.com      # Votre IP publique
terraform -chdir=infra/terraform output # Voir les IPs AWS
```

---

*Documentation maintenue par Yassine Glioual — Projet CloudDelivery*
