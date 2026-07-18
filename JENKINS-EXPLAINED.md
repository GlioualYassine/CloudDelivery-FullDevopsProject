# Rapport Explicatif — Jenkins CI/CD
## Jenkins appliqué au projet CloudDelivery

> **Public cible :** Développeurs, chefs de projet, recruteurs — pas besoin d'être spécialiste DevOps.
> Ce rapport explique Jenkins depuis zéro : ce que c'est, comment ça fonctionne, chaque concept illustré par des exemples, puis tout ce qu'on a fait dans le projet fichier par fichier.

---

## Table des matières

1. [Le problème que Jenkins résout](#1-le-problème-que-jenkins-résout)
2. [Qu'est-ce que Jenkins ?](#2-quest-ce-que-jenkins-)
3. [Comment Jenkins fonctionne](#3-comment-jenkins-fonctionne)
4. [Les concepts clés avec exemples](#4-les-concepts-clés-avec-exemples)
   - 4.1 Pipeline
   - 4.2 Stage
   - 4.3 Step
   - 4.4 Agent
   - 4.5 Credentials
   - 4.6 Triggers
   - 4.7 Post actions
   - 4.8 Parallélisme
5. [CI/CD — La chaîne complète expliquée](#5-cicd--la-chaîne-complète-expliquée)
6. [Ce qu'on a fait dans CloudDelivery — fichier par fichier](#6-ce-quon-a-fait-dans-clouddelivery--fichier-par-fichier)
   - 6.1 Arborescence Jenkins du projet
   - 6.2 Dockerfile Jenkins
   - 6.3 docker-compose.yml (Jenkins local)
   - 6.4 jenkins.yaml (JCasC)
   - 6.5 Jenkinsfile — les 7 stages expliqués ligne par ligne
7. [Le cycle de vie d'un build — de A à Z](#7-le-cycle-de-vie-dun-build--de-a-à-z)
8. [Sécurité dans Jenkins](#8-sécurité-dans-jenkins)
9. [Pourquoi ces choix techniques ?](#9-pourquoi-ces-choix-techniques-)

---

## 1. Le problème que Jenkins résout

### Scénario sans CI/CD

Un développeur modifie le code et veut le déployer :

```
1. Il ouvre son terminal
2. mvn clean package                   → compile + teste (5 min)
3. docker build -t auth:v2 .           → construit l'image (3 min)
4. docker push monrepo/auth:v2         → envoie sur Docker Hub (2 min)
5. ssh ubuntu@server.com               → se connecte au serveur
6. docker compose pull                 → télécharge la nouvelle image
7. docker compose up -d                → relance l'application
8. curl http://server:8080/health      → vérifie que ça fonctionne

→ Total : ~15 min de travail manuel, répété à chaque modification
```

**Les problèmes de cette approche :**

| Problème | Conséquence |
|---|---|
| Tout est manuel | 15 minutes perdues à chaque déploiement |
| Pas de tests systématiques | Un développeur pressé peut oublier de tester |
| Un seul dev peut déployer | Dépendance sur une seule personne |
| Pas de traçabilité | Impossible de savoir "qui a déployé quoi quand" |
| Risque d'erreur humaine | Mauvaise version déployée, serveur cassé |
| Pas de rollback rapide | En cas de bug en prod, retour en arrière complexe |

### La solution : CI/CD avec Jenkins

```
1. Le développeur fait : git push origin develop

→ Jenkins fait AUTOMATIQUEMENT en 15 minutes sans intervention humaine :
   ✓ Clone le code
   ✓ Lance les tests des 4 services (en parallèle)
   ✓ Construit les 5 images Docker (en parallèle)
   ✓ Scanne les vulnérabilités de sécurité
   ✓ Pousse sur Docker Hub
   ✓ Déploie sur le serveur
   ✓ Vérifie que l'application fonctionne

→ Si une étape échoue → TOUT s'arrête → le dev reçoit une notification
→ Si tout passe → le code est en production
```

---

## 2. Qu'est-ce que Jenkins ?

Jenkins est un **serveur d'automatisation open-source** créé en 2004 (initialement sous le nom Hudson). C'est l'outil CI/CD le plus utilisé au monde avec plus de 300 000 installations actives.

**CI = Continuous Integration (Intégration Continue)**
> Chaque modification de code est automatiquement compilée et testée. Les problèmes sont détectés immédiatement, pas 2 semaines plus tard.

**CD = Continuous Delivery/Deployment (Livraison/Déploiement Continu)**
> Le code validé est automatiquement livré (Docker Hub) et déployé (serveur de production).

**Analogie :** Jenkins, c'est comme un assistant qui surveille votre dépôt Git 24h/24. Dès qu'il voit un nouveau commit, il exécute automatiquement toute la séquence de vérification et de déploiement — sans jamais oublier une étape.

**Ce que Jenkins n'est PAS :**
- Ce n'est pas un outil de monitoring (Grafana fait ça)
- Ce n'est pas un gestionnaire de containers (Docker fait ça)
- Ce n'est pas un outil d'infrastructure (Terraform fait ça)

Jenkins est uniquement là pour **orchestrer** : il appelle les autres outils dans le bon ordre.

---

## 3. Comment Jenkins fonctionne

### Architecture de base

```
┌────────────────────────────────────────────────────────────────┐
│                     JENKINS SERVER                             │
│                                                                │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │                  Jenkins Master                           │ │
│  │                                                          │ │
│  │  • Interface web (port 8080 ou 8090 en local)            │ │
│  │  • Lit le Jenkinsfile depuis Git                         │ │
│  │  • Orchestre l'exécution des stages                      │ │
│  │  • Stocke les credentials (secrets)                      │ │
│  │  • Affiche les logs des builds                           │ │
│  │  • Historique des pipelines                              │ │
│  └──────────────────────────────────────────────────────────┘ │
│                                                                │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐ │
│  │  Executor 1  │  │  Executor 2  │  │  Executor N          │ │
│  │  (thread)    │  │  (thread)    │  │  (thread)            │ │
│  │              │  │              │  │                      │ │
│  │  Exécute les │  │  Exécute les │  │  Exécute les         │ │
│  │  stages      │  │  stages      │  │  stages en //        │ │
│  └──────────────┘  └──────────────┘  └──────────────────────┘ │
└────────────────────────────────────────────────────────────────┘
```

### La relation Jenkins ↔ GitHub

```
DÉVELOPPEUR
    │
    │  git push origin develop
    ▼
GITHUB
    │
    │  GitHub envoie un "webhook" (notification HTTP POST)
    │  vers Jenkins : "Nouveau commit sur develop !"
    ▼
JENKINS
    │
    │  1. Clone le repo
    │  2. Lit le fichier "Jenkinsfile" à la racine
    │  3. Exécute chaque stage dans l'ordre
    ▼
RÉSULTAT (SUCCESS ou FAILURE)
```

**Qu'est-ce qu'un webhook ?**
Un webhook est une notification automatique qu'un service (GitHub) envoie à un autre service (Jenkins) quand un événement se produit. C'est GitHub qui appelle Jenkins, pas l'inverse. Ainsi Jenkins n'a pas besoin de vérifier en permanence "est-ce qu'il y a un nouveau commit ?" — GitHub le prévient directement.

### Le Jenkinsfile

Le **Jenkinsfile** est le cœur de tout. C'est un fichier texte écrit en **Groovy** (un langage similaire à Java) qui décrit tout ce que Jenkins doit faire.

Il est stocké à la racine du dépôt Git. Pourquoi dans Git et pas dans Jenkins lui-même ?

- **Versionné** : l'historique des changements du pipeline est dans Git
- **Code review** : un changement du pipeline passe par la même validation que le code
- **Cohérence** : le pipeline évolue avec le code, pas séparément

---

## 4. Les concepts clés avec exemples

### 4.1 Pipeline

Un pipeline est la définition complète du processus automatisé. C'est le conteneur de tout.

```groovy
pipeline {
    agent any        // Où exécuter ? (n'importe quel exécuteur disponible)

    stages {         // Les étapes à exécuter
        stage('Étape 1') { ... }
        stage('Étape 2') { ... }
        stage('Étape 3') { ... }
    }

    post {           // Que faire à la fin (succès ou échec)
        success { echo "Tout s'est bien passé !" }
        failure { echo "Quelque chose a échoué !" }
    }
}
```

---

### 4.2 Stage (étape)

Un stage est une phase logique du pipeline. Dans l'interface Jenkins, chaque stage apparaît comme une colonne colorée (vert = succès, rouge = échec).

```groovy
stage('Tests Unitaires') {
    steps {
        sh 'mvn test'    // Commande à exécuter
    }
}
```

**L'interface Jenkins affiche :**
```
┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐
│ Checkout │→ │  Tests   │→ │  Build   │→ │  Deploy  │
│          │  │          │  │          │  │          │
│  ✓ 12s   │  │  ✓ 3min  │  │  ✓ 5min  │  │  ✓ 1min  │
└──────────┘  └──────────┘  └──────────┘  └──────────┘
```

Si un stage échoue, les suivants ne s'exécutent pas (sauf configuration contraire).

---

### 4.3 Step (action)

Un step est l'action la plus petite possible. Les steps sont à l'intérieur des stages.

```groovy
stage('Exemple') {
    steps {
        // Exécuter une commande shell
        sh 'echo "Hello World"'

        // Exécuter dans un sous-dossier
        dir('mon-service') {
            sh 'mvn clean package'
        }

        // Afficher un message dans les logs
        echo "Le build est terminé"

        // Utiliser un secret stocké dans Jenkins
        withCredentials([string(credentialsId: 'mon-secret', variable: 'MON_SECRET')]) {
            sh 'echo $MON_SECRET | docker login ...'
        }
    }
}
```

---

### 4.4 Agent

L'agent définit **où** s'exécute le pipeline ou un stage. C'est comme choisir "sur quelle machine je fais tourner ça".

```groovy
// Option 1 : N'importe quel exécuteur disponible sur le Jenkins Master
pipeline {
    agent any
    ...
}

// Option 2 : Un container Docker spécifique (Maven dans notre cas)
stage('Tests') {
    agent {
        docker { image 'maven:3.9-eclipse-temurin-17' }
    }
    steps {
        sh 'mvn test'
    }
}

// Option 3 : Un agent distant (machine séparée dédiée aux builds)
agent {
    label 'build-server'
}
```

Dans notre projet, on utilise `agent any` car Jenkins tourne sur un serveur dédié (EC2 ou Docker local) avec Maven installé via le mécanisme `tools`.

---

### 4.5 Credentials (secrets)

Les credentials sont des secrets stockés **chiffrés** dans Jenkins. Le code ne voit jamais la valeur réelle — Jenkins l'injecte au moment de l'exécution.

**Pourquoi ne pas mettre les secrets directement dans le Jenkinsfile ?**

```groovy
// DANGEREUX — ne jamais faire ça :
sh 'docker login -u monusername -p MonMotDePasse123'
// → Le mot de passe apparaîtrait dans les logs
// → Le Jenkinsfile est sur GitHub, visible par tous

// CORRECT — utiliser les credentials Jenkins :
withCredentials([usernamePassword(
    credentialsId: 'dockerhub-credentials',
    usernameVariable: 'USER',
    passwordVariable: 'PASS'
)]) {
    sh 'echo $PASS | docker login -u $USER --password-stdin'
    // → Jenkins masque automatiquement la valeur dans les logs : ****
}
```

**Types de credentials supportés :**

| Type | Usage |
|---|---|
| `usernamePassword` | Login Docker Hub, compte email... |
| `string` (Secret text) | Token API, mot de passe simple |
| `sshUserPrivateKey` | Clé SSH pour se connecter à un serveur |
| `file` | Fichier de configuration secret (kubeconfig, .p12...) |
| `certificate` | Certificat SSL |

---

### 4.6 Triggers (déclencheurs)

Un trigger définit ce qui déclenche l'exécution du pipeline.

```groovy
triggers {
    // Déclenché par un push GitHub (via webhook)
    githubPush()

    // OU : Vérifier toutes les heures s'il y a de nouveaux commits
    pollSCM('H * * * *')

    // OU : Exécuter tous les jours à minuit (format cron)
    cron('0 0 * * *')
}
```

Dans notre projet, on utilise `githubPush()` : GitHub envoie une notification à Jenkins dès qu'un push arrive, ce qui est instantané. Le `pollSCM` est moins recommandé car Jenkins doit vérifier périodiquement (gaspillage).

---

### 4.7 Post actions

Les post actions s'exécutent après tous les stages, quelle que soit l'issue.

```groovy
post {
    always {
        // Toujours exécuté (succès ET échec)
        sh 'docker logout || true'
        cleanWs()    // Nettoie l'espace de travail Jenkins
    }
    success {
        // Seulement si tout a réussi
        echo "✓ Build ${env.BUILD_NUMBER} déployé avec succès"
        // Ici on pourrait envoyer un email de succès
    }
    failure {
        // Seulement si quelque chose a échoué
        echo "✗ Build ${env.BUILD_NUMBER} échoué — voir les logs"
        // Ici on pourrait envoyer une alerte Slack/email
    }
    unstable {
        // Si les tests ont partiellement échoué
        echo "Tests en état instable"
    }
}
```

**Pourquoi `cleanWs()` dans `always` ?**
Jenkins clone le dépôt et génère des fichiers (`.jar`, images Docker temporaires...) dans un dossier de travail. Sans nettoyage, le disque se remplit au fil des builds. `cleanWs()` supprime tout après chaque build.

---

### 4.8 Parallélisme

Le parallélisme permet d'exécuter plusieurs stages simultanément, réduisant le temps total.

```groovy
stage('Tests') {
    parallel {
        stage('Test Service A') {
            steps { sh 'cd serviceA && mvn test' }
        }
        stage('Test Service B') {
            steps { sh 'cd serviceB && mvn test' }
        }
        stage('Test Service C') {
            steps { sh 'cd serviceC && mvn test' }
        }
    }
}
```

**Impact sur le temps de build :**
```
Sans parallélisme :
   Service A (3min) → Service B (3min) → Service C (3min) = 9 minutes

Avec parallélisme :
   Service A ─┐
   Service B ─┼→ 3 minutes
   Service C ─┘
```

---

## 5. CI/CD — La chaîne complète expliquée

### CI — Intégration Continue

```
Chaque développeur pousse son code plusieurs fois par jour.
Jenkins vérifie automatiquement que le code est correct.

Avantage : Les bugs sont détectés le jour même, pas en fin de sprint.
```

### CD — Livraison Continue (Continuous Delivery)

```
Le code qui passe les tests est automatiquement packagé
(ici : image Docker) et disponible pour déploiement.

On déploie manuellement quand on veut.
```

### CD — Déploiement Continu (Continuous Deployment)

```
Le code qui passe les tests EST automatiquement déployé
en production, sans intervention humaine.

C'est ce qu'on fait dans CloudDelivery (sur la branche develop).
```

### La règle fondamentale du CI/CD

> **"Si le pipeline est rouge, c'est la priorité absolue de l'équipe. Personne ne pousse de nouveau code avant que le pipeline soit vert."**

Un pipeline qui reste rouge perd sa valeur : les développeurs l'ignorent, et la qualité du code se dégrade.

---

## 6. Ce qu'on a fait dans CloudDelivery — fichier par fichier

### 6.1 Arborescence Jenkins du projet

```
CloudDelivery-FullDevopsProject/
│
├── Jenkinsfile                    ← Pipeline principal (lu par Jenkins depuis Git)
│
└── jenkins/                       ← Setup Jenkins local (pour tester sans AWS)
    ├── Dockerfile                 ← Image Jenkins personnalisée avec Docker CLI
    ├── docker-compose.yml         ← Lance Jenkins en local (port 8090)
    ├── .env.example               ← Template des variables secrètes
    ├── .gitignore                 ← Exclut .env (jamais commité)
    └── casc/
        └── jenkins.yaml           ← Configuration automatique Jenkins (JCasC)
```

---

### 6.2 `jenkins/Dockerfile` — L'image Jenkins personnalisée

```dockerfile
FROM jenkins/jenkins:lts-jdk17
```

On part de l'image officielle Jenkins avec Java 17 inclus. Le suffixe `lts` (Long-Term Support) garantit une version stable et maintenue pendant 2 ans.

```dockerfile
USER root

RUN apt-get update && \
    apt-get install -y ca-certificates curl gnupg && \
    install -m 0755 -d /etc/apt/keyrings && \
    curl -fsSL https://download.docker.com/linux/debian/gpg | \
        gpg --dearmor -o /etc/apt/keyrings/docker.gpg && \
    echo "deb [arch=amd64 signed-by=/etc/apt/keyrings/docker.gpg] \
         https://download.docker.com/linux/debian bookworm stable" \
         > /etc/apt/sources.list.d/docker.list && \
    apt-get update && \
    apt-get install -y docker-ce-cli
```

**Pourquoi installer Docker CLI dans Jenkins ?**

Notre Jenkinsfile contient des commandes comme `docker build`, `docker push`, `docker run`. Pour que Jenkins puisse les exécuter, Docker CLI doit être installé dans le container Jenkins.

Attention : on installe **Docker CLI** (le client), pas **Docker daemon** (le serveur). Le daemon tourne sur l'hôte. Jenkins s'y connecte via le socket.

```dockerfile
RUN groupadd -f -g 999 docker && usermod -aG docker jenkins
```

Ajoute l'utilisateur `jenkins` au groupe `docker`. Sans ça, Jenkins ne peut pas exécuter les commandes Docker (permission refusée sur le socket).

```dockerfile
ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false -Xmx1g"
ENV CASC_JENKINS_CONFIG=/var/jenkins_casc
```

- `-Djenkins.install.runSetupWizard=false` : désactive l'assistant de configuration au premier démarrage (JCasC s'en charge)
- `-Xmx1g` : limite la JVM Jenkins à 1 Go de RAM maximum
- `CASC_JENKINS_CONFIG` : dit à JCasC où trouver son fichier de configuration

---

### 6.3 `jenkins/docker-compose.yml` — Jenkins local

```yaml
services:
  jenkins:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: clouddelivery-jenkins-local
    ports:
      - "8090:8080"       # Port 8090 sur votre machine → 8080 dans Jenkins
```

**Pourquoi 8090 et pas 8080 ?**
Le port 8080 est souvent utilisé par d'autres services en développement local (Tomcat, Spring Boot...). On utilise 8090 pour éviter les conflits.

```yaml
    volumes:
      - jenkins_home:/var/jenkins_home
```

**Qu'est-ce que `jenkins_home` ?**
C'est un volume Docker nommé. Il persiste les données Jenkins (configurations, historique des builds, plugins installés) même si le container est arrêté ou supprimé. Sans volume, vous perdriez toute la configuration à chaque redémarrage.

```yaml
      - ./casc:/var/jenkins_casc:ro
```

Monte le dossier `casc/` local à l'intérieur du container en lecture seule (`ro`). Jenkins lira son fichier de configuration depuis ce dossier au démarrage.

```yaml
      - /var/run/docker.sock:/var/run/docker.sock
```

**C'est la ligne la plus importante.**

`/var/run/docker.sock` est le socket Unix du daemon Docker sur votre machine hôte. En le montant dans le container Jenkins, on permet à Jenkins d'envoyer des commandes au Docker de l'hôte. C'est ce qu'on appelle "Docker-out-of-Docker" (DooD).

```
Jenkins container
    │
    │  docker build ...
    │
    ▼
/var/run/docker.sock (socket partagé)
    │
    ▼
Docker daemon (sur votre Mac)
    │
    │  Construit l'image
    ▼
Image disponible sur votre machine
```

```yaml
    env_file:
      - .env
```

Charge les variables d'environnement depuis le fichier `.env` (qui contient les secrets). Ce fichier n'est jamais commité sur Git.

---

### 6.4 `jenkins/casc/jenkins.yaml` — Configuration as Code (JCasC)

JCasC (Jenkins Configuration as Code) est un plugin Jenkins qui permet de configurer Jenkins entièrement via un fichier YAML. Sans JCasC, il faudrait cliquer dans l'interface pour tout configurer à la main — une procédure longue, non reproductible et impossible à versionner.

**Avantage concret :** Si Jenkins plante et que vous devez le recréer, il se reconfigure automatiquement en quelques secondes grâce au fichier YAML dans Git.

#### Section 1 — Identité Jenkins

```yaml
jenkins:
  systemMessage: "CloudDelivery CI/CD — Local Jenkins"
  numExecutors: 2
  mode: NORMAL
```

- `systemMessage` : message affiché sur la page d'accueil Jenkins (identification visuelle)
- `numExecutors: 2` : nombre de builds pouvant s'exécuter simultanément. Avec 2 executors, Jenkins peut travailler sur 2 pipelines en même temps. Sur un `t3.medium` (2 vCPU), 2 executors est le bon compromis.
- `mode: NORMAL` : le master peut aussi exécuter des builds (par opposition à `EXCLUSIVE` où seuls les agents externes le font)

#### Section 2 — Sécurité et utilisateurs

```yaml
  securityRealm:
    local:
      allowsSignup: false
      users:
        - id: admin
          password: "${JENKINS_ADMIN_PASSWORD}"
```

- `securityRealm: local` : Jenkins gère ses propres utilisateurs (pas d'LDAP, pas d'OAuth dans ce projet)
- `allowsSignup: false` : personne ne peut créer un compte depuis l'interface web. Seul l'admin défini ici peut se connecter.
- `password: "${JENKINS_ADMIN_PASSWORD}"` : la notation `${...}` dit à JCasC de lire la variable d'environnement `JENKINS_ADMIN_PASSWORD` au démarrage. Le mot de passe n'est jamais écrit en clair dans le fichier.

```yaml
  authorizationStrategy:
    loggedInUsersCanDoAnything:
      allowAnonymousRead: false
```

- `loggedInUsersCanDoAnything` : tout utilisateur connecté a tous les droits (admin)
- `allowAnonymousRead: false` : un visiteur non connecté ne peut rien voir, pas même les logs. Sans ça, n'importe qui pourrait lire vos builds et vos logs sur internet.

#### Section 3 — Credentials (secrets)

```yaml
credentials:
  system:
    domainCredentials:
      - credentials:
          - usernamePassword:
              scope: GLOBAL
              id: dockerhub-credentials
              username: "${DOCKERHUB_USERNAME}"
              password: "${DOCKERHUB_TOKEN}"
              description: "Docker Hub — username + token"
```

JCasC crée automatiquement le credential `dockerhub-credentials` dans Jenkins. C'est l'ID que le Jenkinsfile utilise pour se connecter à Docker Hub.

**Pourquoi un `id` ?**
L'ID est la référence que le Jenkinsfile utilise :
```groovy
withCredentials([usernamePassword(credentialsId: 'dockerhub-credentials', ...)]) {
    // Jenkins injecte automatiquement DOCKER_USER et DOCKER_PASS
}
```
Le Jenkinsfile ne connaît que l'ID, jamais la valeur réelle.

#### Section 4 — Configuration des outils

```yaml
tool:
  maven:
    installations:
      - name: Maven-3.9
        properties:
          - installSource:
              installers:
                - maven:
                    id: "3.9.6"
```

Cette section dit à Jenkins : "Installe automatiquement Maven 3.9.6 et appelle-le `Maven-3.9`". Lors du premier build, Jenkins télécharge Maven 3.9.6 et le rend disponible. Le Jenkinsfile y accède via :

```groovy
tools {
    maven 'Maven-3.9'    // ← Même nom que dans le JCasC
}
```

Après cette déclaration, `mvn` est disponible sur le PATH dans tous les stages.

---

### 6.5 `Jenkinsfile` — Les 7 stages expliqués

#### Déclarations globales

```groovy
pipeline {
    agent any

    tools {
        maven 'Maven-3.9'    // Maven disponible sur le PATH dans tout le pipeline
    }
```

`agent any` : le pipeline s'exécute sur n'importe quel exécuteur Jenkins disponible. Dans notre cas, c'est le master lui-même (un seul serveur Jenkins).

```groovy
    environment {
        DOCKERHUB_USERNAME = credentials('dockerhub-credentials')
        DOCKER_IMAGE_PREFIX = "${DOCKERHUB_USERNAME}/clouddelivery"
        IMAGE_TAG = "${env.GIT_COMMIT[0..6]}"
        SOURCE_DIR = "Source Code"
    }
```

**Variables d'environnement disponibles dans tout le pipeline :**

- `DOCKERHUB_USERNAME` : Jenkins lit le credential `dockerhub-credentials` et injecte le username
- `DOCKER_IMAGE_PREFIX` : préfixe de toutes les images Docker → `yassine/clouddelivery`
- `IMAGE_TAG` : les 7 premiers caractères du SHA Git du commit actuel (ex: `a1b2c3d`)
- `SOURCE_DIR` : le dossier qui contient les microservices

**Pourquoi le SHA Git comme tag d'image ?**
```
yassine/clouddelivery-auth:a1b2c3d    ← Tag SHA Git (immuable, traçable)
yassine/clouddelivery-auth:latest     ← Tag flottant (toujours la dernière version)
```

Le tag SHA permet de savoir exactement quel commit a produit cette image. Si un bug apparaît en production sur la version `a1b2c3d`, vous pouvez retrouver le commit exact et faire un rollback précis vers la version précédente `f9e8d7c`.

```groovy
    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))    // Garde les 10 derniers builds
        timeout(time: 45, unit: 'MINUTES')               // Tue le build après 45 min
        disableConcurrentBuilds()                        // Un seul build à la fois
        timestamps()                                     // Affiche l'heure dans les logs
    }
```

- `buildDiscarder` : sans ça, Jenkins accumule tous les builds → remplit le disque
- `timeout(45 min)` : si un build reste bloqué (réseau coupé, boucle infinie...), il s'arrête automatiquement. Sans ça, un build bloqué monopolise un executor pour toujours.
- `disableConcurrentBuilds` : deux builds du même projet ne peuvent pas tourner simultanément. Évite les conflits sur les images Docker (même tag écrit en même temps).

---

#### Stage 1 — Checkout

```groovy
stage('Checkout') {
    steps {
        checkout scm
        script {
            env.IMAGE_TAG = sh(returnStdout: true,
                               script: 'git rev-parse --short HEAD').trim()
            echo "Building commit: ${env.IMAGE_TAG} on branch: ${env.BRANCH_NAME}"
        }
    }
}
```

`checkout scm` : Jenkins clone le dépôt Git configuré dans le job. Le code source est disponible dans le workspace.

`git rev-parse --short HEAD` : récupère les 7 premiers caractères du SHA du commit actuel. On réassigne `IMAGE_TAG` ici car la variable d'environnement du bloc `environment {}` est calculée avant que le code soit cloné.

**Log typique :**
```
[Pipeline] checkout
Cloning repository https://github.com/GlioualYassine/CloudDelivery...
Building commit: a1b2c3d on branch: develop
```

---

#### Stage 2 — Tests unitaires (parallèle)

```groovy
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
        stage('Notification Tests') { ... }
        stage('Order Tests')        { ... }
        stage('Delivery Tests')     { ... }
    }
}
```

**`dir("...")`** : change le répertoire courant avant d'exécuter les commandes. Équivalent de `cd authentication-service && mvn test`.

**`mvn test -B --no-transfer-progress`** :
- `-B` (batch mode) : pas de couleurs ANSI, sortie optimisée pour les logs CI
- `--no-transfer-progress` : supprime les barres de progression du téléchargement Maven (qui polluent les logs)

**`junit testResults: ".../*.xml"`** : Jenkins lit les rapports XML générés par Surefire (le plugin Maven de tests) et les affiche dans l'interface sous forme de graphiques. Si un test échoue, Jenkins affiche directement quel test et quelle assertion a échoué.

**Pourquoi `allowEmptyResults: true` ?**
Si Maven échoue avant même de lancer les tests (erreur de compilation), aucun rapport XML n'est généré. Sans `allowEmptyResults`, Jenkins afficherait une erreur supplémentaire "aucun rapport trouvé" qui masquerait la vraie erreur.

**Les 4 services testés simultanément :**
```
Auth Tests         (18 tests) ─┐
Notification Tests (7 tests)  ─┼→ ~3 minutes (au lieu de ~12 en séquentiel)
Order Tests        (16 tests) ─┤
Delivery Tests     (32 tests) ─┘
```

---

#### Stage 3 — Build des images Docker (parallèle)

```groovy
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
        // ... Build Notification, Order, Delivery, Gateway
    }
}
```

**Deux tags par image :**
```
yassine/clouddelivery-auth:a1b2c3d   ← SHA Git — permanent, traçable
yassine/clouddelivery-auth:latest    ← Toujours la dernière version
```

**Pourquoi construire les images APRÈS les tests ?**
Si les tests échouent (Stage 2), on ne construit jamais les images. On économise 5-8 minutes de build inutile et on ne pollue pas Docker Hub avec des images cassées.

**5 builds en parallèle :**
```
auth-service        ─┐
notification-service ─┤
order-service        ─┼→ ~5 minutes (au lieu de ~25 en séquentiel)
delivery-service     ─┤
api-gateway          ─┘
```

---

#### Stage 4 — Scan de sécurité Trivy

```groovy
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
```

**Qu'est-ce que Trivy ?**

Trivy est un scanner de vulnérabilités open-source créé par Aqua Security. Il analyse une image Docker et liste toutes les CVE (Common Vulnerabilities and Exposures) connues dans les packages qu'elle contient.

**Exemple de rapport Trivy :**
```
eclipse-temurin:17-jre-alpine (alpine 3.19.1)
=========================================
Total: 2 (CRITICAL: 0, HIGH: 2)

┌─────────────────┬──────────────────┬──────────┬───────────────────┐
│     Library     │  Vulnerability   │ Severity │     Fixed In      │
├─────────────────┼──────────────────┼──────────┼───────────────────┤
│ libssl3         │ CVE-2024-XXXXX   │ HIGH     │ 3.1.4-r1          │
│ busybox         │ CVE-2023-XXXXX   │ HIGH     │ 1.36.1-r16        │
└─────────────────┴──────────────────┴──────────┴───────────────────┘
```

**Les options Trivy choisies :**

- `--exit-code 1` : Trivy retourne le code d'erreur 1 si des vulnérabilités sont trouvées → Jenkins marque le build comme échoué
- `--severity CRITICAL` : on bloque seulement sur les vulnérabilités CRITIQUES (les HIGH/MEDIUM/LOW passent)
- `--ignore-unfixed` : ignore les CVE pour lesquelles il n'existe pas encore de correctif. Inutile de bloquer le déploiement si le fournisseur n'a pas encore publié de patch.
- `-v $HOME/.cache/trivy:/root/.cache/trivy` : met en cache la base de données CVE localement → évite de la re-télécharger à chaque build (elle fait ~200 Mo)

**Pourquoi `|| true` à la fin ?**

On met `|| true` pour que même si Trivy trouve des CRITIQUES, le pipeline continue (en mode "warning" plutôt qu'"erreur bloquante"). Dans un projet production mature, on retirerait le `|| true` pour bloquer vraiment. Ici c'est un choix pragmatique pour un projet de portfolio.

---

#### Stage 5 — Push vers Docker Hub

```groovy
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
                    sh "docker push ${DOCKER_IMAGE_PREFIX}-${svc}:${IMAGE_TAG}"
                    sh "docker push ${DOCKER_IMAGE_PREFIX}-${svc}:latest"
                }
            }
        }
    }
}
```

**`when { anyOf { branch 'develop'; branch 'main' } }`**

Ce stage ne s'exécute QUE sur les branches `develop` et `main`. Sur une branche de feature (`feature/mon-truc`), les images ne sont pas publiées. Logique : on ne veut pas polluer Docker Hub avec des images de features incomplètes.

**`withCredentials([...])`**

Jenkins injecte les credentials `dockerhub-credentials` dans les variables `DOCKER_USER` et `DOCKER_PASS`. Ces variables sont masquées dans les logs (`****`). À la sortie du bloc `withCredentials`, les variables sont effacées de la mémoire.

**`echo $DOCKER_PASS | docker login --password-stdin`**

On envoie le token via stdin plutôt que via `-p motdepasse`. Pourquoi ? La commande `-p motdepasse` apparaît dans la liste des processus système (`ps aux`), lisible par n'importe quel utilisateur du serveur. Via stdin, le mot de passe n'est jamais visible.

**Résultat sur Docker Hub après le push :**
```
hub.docker.com/r/yassine/
├── clouddelivery-auth        → tags: a1b2c3d, latest
├── clouddelivery-notification → tags: a1b2c3d, latest
├── clouddelivery-order        → tags: a1b2c3d, latest
├── clouddelivery-delivery     → tags: a1b2c3d, latest
└── clouddelivery-gateway      → tags: a1b2c3d, latest
```

---

#### Stage 6 — Déploiement sur l'App Server

```groovy
stage('Deploy') {
    when {
        anyOf { branch 'develop'; branch 'main' }
    }
    steps {
        withCredentials([
            sshUserPrivateKey(credentialsId: 'app-server-ssh', keyFileVariable: 'SSH_KEY'),
            string(credentialsId: 'mail-username', variable: 'MAIL_USERNAME'),
            string(credentialsId: 'mail-password', variable: 'MAIL_PASSWORD'),
            usernamePassword(credentialsId: 'dockerhub-credentials',
                             usernameVariable: 'DOCKER_USER',
                             passwordVariable: 'DOCKER_PASS')
        ]) {
            sh """
                ssh -i \$SSH_KEY -o StrictHostKeyChecking=no ubuntu@\${APP_SERVER_IP} '
                    set -e
                    cd /opt/clouddelivery/"Source Code"
                    echo \$DOCKER_PASS | docker login -u \$DOCKER_USER --password-stdin
                    export IMAGE_TAG=${IMAGE_TAG}
                    docker compose -f docker-compose.prod.yml pull
                    docker compose -f docker-compose.prod.yml up -d --remove-orphans
                    docker image prune -f
                '
            """
        }
    }
}
```

**Ce que ce stage fait, étape par étape :**

1. Jenkins s'authentifie auprès de 4 credentials différents simultanément
2. Jenkins ouvre une connexion SSH vers l'App Server EC2 en utilisant la clé privée
3. Sur l'App Server, en une seule session SSH :
   - `docker login` → s'authentifie à Docker Hub
   - `docker compose pull` → télécharge les nouvelles images taguées `${IMAGE_TAG}`
   - `docker compose up -d` → relance les containers avec les nouvelles images
   - `--remove-orphans` → supprime les containers qui ne sont plus dans le compose
   - `docker image prune -f` → supprime les anciennes images pour libérer de l'espace

**`set -e`** : si une commande échoue dans le script SSH, tout s'arrête immédiatement. Sans ça, le script pourrait continuer même si `docker compose pull` a échoué.

**Pourquoi `docker compose up -d` et pas `docker compose restart` ?**
- `restart` redémarre les containers existants avec l'ancienne image
- `up -d` compare ce qui tourne avec le compose file, et met à jour uniquement ce qui a changé. Si une image a une nouvelle version, le container correspondant est recréé avec la nouvelle image.

---

#### Stage 7 — Smoke Test

```groovy
stage('Smoke Test') {
    when {
        anyOf { branch 'develop'; branch 'main' }
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
```

**Qu'est-ce qu'un smoke test ?**

Un smoke test (test de fumée) est la vérification minimale qu'une application fonctionne après déploiement. L'origine du terme vient de l'électronique : on branche un circuit et on regarde si de la fumée sort. Si non, au moins il ne brûle pas.

Ici le smoke test est : "L'API Gateway répond-elle à la requête la plus basique ?"

**`curl -sf http://...:8080/actuator/health | grep -q '"status":"UP"'`**
- `-s` (silent) : pas d'affichage de la progression
- `-f` (fail) : retourne une erreur si le code HTTP est 4xx ou 5xx
- `--max-time 10` : abandonne si pas de réponse en 10 secondes
- `grep -q '"status":"UP"'` : vérifie que la réponse contient bien `"status":"UP"`

**Réponse attendue de Spring Actuator :**
```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "kafka": { "status": "UP" }
  }
}
```

**`retry(3)` avec `sleep(20s)`**

Après `docker compose up`, les containers prennent quelques secondes à démarrer (Spring Boot démarre en 20-40 secondes). `retry(3)` tente 3 fois avec 20 secondes entre chaque tentative. Total : jusqu'à 60 secondes d'attente avant d'échouer.

---

#### Post actions

```groovy
post {
    always {
        sh 'docker logout || true'    // Déconnecte Docker Hub (sécurité)
        cleanWs()                     // Vide l'espace de travail
    }
    success {
        echo "Pipeline succeeded — ${env.IMAGE_TAG} deployed"
    }
    failure {
        echo "Pipeline failed — check logs above"
    }
}
```

**Pourquoi `docker logout` dans `always` ?**
Si le build échoue après la connexion Docker Hub, on veut quand même se déconnecter. Sans `docker logout`, les credentials Docker Hub restent en mémoire sur le serveur Jenkins jusqu'au prochain login — risque si quelqu'un accède au serveur.

**`|| true`** : même si `docker logout` échoue (déjà déconnecté), on ne veut pas que ça fasse échouer le post. Le `|| true` garantit que la commande "réussit" toujours.

---

## 7. Le cycle de vie d'un build — de A à Z

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  DÉVELOPPEUR : git push origin develop
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  T+0s   GitHub reçoit le push
         → GitHub envoie un webhook POST à Jenkins :
           POST http://jenkins-ip:8080/github-webhook/

  T+2s   Jenkins reçoit la notification
         → Crée un nouveau "Build #42" dans la queue

  T+5s   Jenkins clône le dépôt Git
         → Lit le Jenkinsfile
         → Calcule IMAGE_TAG = "a1b2c3d"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  STAGE 1 — Checkout (T+5s → T+30s)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  ✓ Code source disponible dans le workspace

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  STAGE 2 — Tests unitaires (T+30s → T+3min30s)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  Auth (18 tests)         ─┐
  Notification (7 tests)  ─┤ → exécution parallèle
  Order (16 tests)        ─┤    ~3 minutes
  Delivery (32 tests)     ─┘

  SI ÉCHEC → Pipeline s'arrête, images non construites, rien déployé

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  STAGE 3 — Build Docker (T+3min30s → T+9min)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  auth:a1b2c3d         ─┐
  notification:a1b2c3d ─┤
  order:a1b2c3d        ─┼→ 5 builds en parallèle (~5 min)
  delivery:a1b2c3d     ─┤
  gateway:a1b2c3d      ─┘

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  STAGE 4 — Trivy CVE Scan (T+9min → T+12min)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  Analyse les 5 images, rapport affiché dans les logs

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  STAGE 5 — Push Docker Hub (T+12min → T+17min)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  docker push 5 × 2 tags (SHA + latest) → Docker Hub

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  STAGE 6 — Déploiement SSH (T+17min → T+19min)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  SSH → App Server → docker compose pull + up -d

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  STAGE 7 — Smoke Test (T+19min → T+21min)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  curl http://app-server:8080/actuator/health → {"status":"UP"}

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  POST — Nettoyage (T+21min)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  docker logout + cleanWs()

  RÉSULTAT : Build #42 — SUCCESS
  "Pipeline succeeded — a1b2c3d deployed"
  Durée totale : ~21 minutes
```

---

## 8. Sécurité dans Jenkins

### Les credentials ne sont jamais en clair dans les logs

Jenkins masque automatiquement les valeurs des credentials dans les logs :
```
[Pipeline] sh
+ docker login -u monusername --password-stdin
+ echo ****
WARNING! Using --password via the CLI is insecure. Use --password-stdin.
Login Succeeded
```

### Accès limité à l'interface

`allowAnonymousRead: false` garantit que l'interface Jenkins n'est pas publique. Sans ça, n'importe qui pourrait voir vos pipelines, vos logs, vos déploiements.

### Clé SSH stockée en credential

La clé SSH pour se connecter à l'App Server est un credential Jenkins (`app-server-ssh`). Elle n'est jamais écrite dans un fichier sur le disque, seulement injectée en mémoire pendant l'exécution du stage.

### Docker logout systématique

Le bloc `post { always { sh 'docker logout' } }` garantit que la session Docker Hub est fermée à la fin de chaque build, même en cas d'échec.

### Tokens Docker Hub, pas de mots de passe

Sur Docker Hub, on génère un **Access Token** (pas le vrai mot de passe du compte). Si ce token est compromis, on le révoque sur Docker Hub sans changer le mot de passe du compte. Le token peut aussi être limité en droits (lecture seule, ou lecture+écriture seulement sur certains repos).

---

## 9. Pourquoi ces choix techniques ?

### Pourquoi Jenkins et pas GitHub Actions ?

| Critère | Jenkins | GitHub Actions |
|---|---|---|
| Hébergement | Sur votre serveur (contrôle total) | Sur les serveurs GitHub |
| Coût | Gratuit (hors serveur) | Gratuit jusqu'à 2000 min/mois |
| Personnalisation | Illimitée (plugins, Dockerfile custom) | Limitée aux actions GitHub |
| Plugins disponibles | 1800+ plugins | ~15 000 actions marketplace |
| Visibilité CV | Très valorisé ("Jenkins" très demandé en entreprise) | Commun (tout le monde l'utilise) |
| Self-hosted runners | Possible | Possible |
| Complexité | Plus complexe à configurer | Plus simple |

Pour un projet DevOps portfolio, **Jenkins est plus valorisant** car c'est ce qu'on trouve dans les entreprises avec des pipelines complexes. GitHub Actions est plus simple mais moins différenciant.

### Pourquoi JCasC et pas la configuration manuelle ?

Sans JCasC, configurer Jenkins prend 30-45 minutes de clics dans l'interface. Avec JCasC, c'est automatique au démarrage. Si le serveur Jenkins est détruit et recréé (ce qui arrive avec `terraform destroy`), JCasC reconfigure tout en quelques secondes.

### Pourquoi les tests AVANT le build d'images Docker ?

L'ordre des stages est délibéré :
```
Tests → Build → Scan → Push → Deploy
```

Si les tests échouent (étape 2), on ne construit jamais les images (étape 3). Cela économise :
- 5-8 minutes de build Docker
- De l'espace sur Docker Hub
- De la confusion ("pourquoi cette image cassée est-elle sur Docker Hub ?")

### Pourquoi deux tags (`SHA` et `latest`) par image ?

- **Tag `latest`** : permet à `docker compose pull` sur le serveur de toujours récupérer la dernière version sans connaître le SHA
- **Tag `SHA` (`a1b2c3d`)** : permet le rollback précis. Si `latest` est buggé, on peut redéployer `a1b2c3d-1` (le SHA précédent) sans ambiguïté

### Pourquoi `disableConcurrentBuilds()` ?

Sans cette option, si deux commits arrivent en 30 secondes, deux builds démarrent en parallèle. Ils vont tous les deux essayer de pousser `auth:latest` sur Docker Hub en même temps → race condition. Le tag `latest` pourrait finir par pointer vers le build le plus lent, pas le plus récent.

---

*Rapport généré le 2026-07-18 pour le projet CloudDelivery — GlioualYassine*
