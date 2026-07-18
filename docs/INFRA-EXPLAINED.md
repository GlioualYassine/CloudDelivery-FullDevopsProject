# Rapport Explicatif — Infrastructure as Code
## Terraform & Ansible appliqués au projet CloudDelivery

> **Public cible :** Développeurs, chefs de projet, recruteurs — pas besoin d'être spécialiste DevOps.
> Ce rapport explique chaque outil, chaque concept, chaque fichier, et chaque décision prise pour le projet.

---

## Table des matières

1. [Le problème que ces outils résolvent](#1-le-problème-que-ces-outils-résolvent)
2. [Terraform — Créer l'infrastructure](#2-terraform--créer-linfrastructure)
   - 2.1 Qu'est-ce que Terraform ?
   - 2.2 Comment Terraform fonctionne
   - 2.3 Les concepts clés avec exemples
   - 2.4 Ce qu'on a fait dans CloudDelivery — fichier par fichier
3. [Ansible — Configurer les serveurs](#3-ansible--configurer-les-serveurs)
   - 3.1 Qu'est-ce qu'Ansible ?
   - 3.2 Comment Ansible fonctionne
   - 3.3 Les concepts clés avec exemples
   - 3.4 Ce qu'on a fait dans CloudDelivery — fichier par fichier
4. [Le Jenkinsfile — La chaîne CI/CD](#4-le-jenkinsfile--la-chaîne-cicd)
5. [Les scripts — Automatiser les opérations](#5-les-scripts--automatiser-les-opérations)
6. [L'arborescence complète expliquée](#6-larborescence-complète-expliquée)
7. [Le flux complet de déploiement](#7-le-flux-complet-de-déploiement)
8. [Pourquoi ces choix techniques ?](#8-pourquoi-ces-choix-techniques)

---

## 1. Le problème que ces outils résolvent

### La méthode "ancienne" — configurer à la main

Imaginons que vous devez déployer une application sur un serveur cloud.
Sans outils d'infrastructure, voici ce que vous feriez :

1. Vous allez sur la console AWS avec votre navigateur
2. Vous cliquez sur "Créer une instance EC2"
3. Vous choisissez le type de machine, le réseau, les règles de sécurité...
4. Une fois le serveur créé, vous vous connectez en SSH
5. Vous tapez manuellement : `apt install docker`, `apt install java`, `git clone ...`
6. Vous configurez chaque fichier de configuration à la main
7. Vous faites pareil pour le 2e serveur, le 3e...

**Les problèmes de cette approche :**

| Problème | Conséquence |
|---|---|
| Tout est fait à la main | Si vous refaites ça demain, vous pouvez oublier une étape |
| Pas reproductible | Serveur A et serveur B ont des différences invisibles |
| Pas documenté | Un nouveau collègue ne sait pas comment reproduire |
| Pas versionnable | Impossible de savoir "quelle config était en place le 15 janvier ?" |
| Lent | Configurer 10 serveurs = 10× le temps |
| Coûteux en erreurs | Une faute de frappe peut tout casser |

### La solution : Infrastructure as Code (IaC)

L'idée est simple : **écrire l'infrastructure comme on écrit du code.**

Au lieu de cliquer dans une interface, vous écrivez dans un fichier texte :
- "Je veux 2 serveurs EC2 de type t3.medium"
- "Je veux un réseau VPC avec ce CIDR"
- "Sur ce serveur, installe Docker, puis lance Jenkins"

Ce fichier texte est :
- **Versionnable** → stocké dans Git, historique complet
- **Reproductible** → exécuté 10 fois = même résultat
- **Partageable** → un collègue peut lire et comprendre l'infra
- **Automatisable** → Jenkins peut l'exécuter sans intervention humaine

**Deux outils couvrent deux phases différentes :**

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│   TERRAFORM          →    "Créer les machines sur AWS"          │
│   (Provisioning)          VPC, EC2, Security Groups, etc.       │
│                                                                 │
│   ANSIBLE            →    "Configurer ce qu'il y a dedans"      │
│   (Configuration)         Installer Docker, lancer Jenkins, etc.│
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. Terraform — Créer l'infrastructure

### 2.1 Qu'est-ce que Terraform ?

Terraform est un outil créé par HashiCorp (2014). Il permet de **décrire** votre infrastructure cloud dans des fichiers `.tf` (format HCL — HashiCorp Configuration Language), puis de la **créer automatiquement** sur n'importe quel cloud (AWS, Azure, GCP...).

**Analogie simple :** Terraform, c'est comme un architecte qui lit vos plans (les fichiers `.tf`) et construit la maison (l'infrastructure cloud) exactement comme décrit.

### 2.2 Comment Terraform fonctionne

Le cycle de vie Terraform s'articule autour de 3 commandes :

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│              │     │              │     │              │
│  terraform   │ --> │  terraform   │ --> │  terraform   │
│    init      │     │    plan      │     │    apply     │
│              │     │              │     │              │
│ Télécharge   │     │ Prévisualise │     │ Crée vraiment│
│ les plugins  │     │ les          │     │ les ressources│
│ (providers)  │     │ changements  │     │ sur AWS      │
└──────────────┘     └──────────────┘     └──────────────┘
```

**Étape 1 — `terraform init`**

Terraform lit vos fichiers `.tf` et télécharge les "providers" nécessaires.
Un provider est un plugin qui sait comment parler à un cloud.

```bash
$ terraform init

Initializing provider plugins...
- Finding hashicorp/aws versions matching "~> 5.0"...
- Installing hashicorp/aws v5.31.0...
```

**Étape 2 — `terraform plan`**

Terraform compare l'état actuel de votre infrastructure (ce qui existe déjà sur AWS) avec ce que vous avez écrit dans vos fichiers `.tf`, et affiche ce qu'il va créer/modifier/supprimer.

```bash
$ terraform plan

Plan: 8 to add, 0 to change, 0 to destroy.

+ aws_vpc.main           → va créer un VPC
+ aws_subnet.public      → va créer un sous-réseau
+ aws_instance.jenkins   → va créer un serveur EC2
...
```

**Étape 3 — `terraform apply`**

Terraform exécute le plan et crée réellement les ressources sur AWS.

**Le fichier d'état (terraform.tfstate)**

Terraform garde en mémoire ce qu'il a créé dans un fichier appelé `terraform.tfstate`. C'est sa "mémoire". Grâce à ce fichier, si vous relancez `terraform apply` une 2e fois, il sait déjà que le VPC existe et ne le recrée pas.

```
terraform.tfstate (JSON) = "Ce que Terraform a créé jusqu'ici"
```

**`terraform destroy`**

Supprime tout ce qui a été créé. Utilisé pour stopper les coûts quand on ne travaille pas.

```bash
$ terraform destroy
Destroy complete! Resources: 8 destroyed.
```

### 2.3 Les concepts clés avec exemples

#### Concept 1 — La ressource (`resource`)

Une ressource est la brique de base. Elle représente un objet à créer sur AWS.

```hcl
# Exemple : créer un VPC
resource "aws_vpc" "main" {
  cidr_block = "10.0.0.0/16"

  tags = {
    Name = "mon-vpc"
  }
}
```

- `aws_vpc` = le type de ressource (un VPC Amazon)
- `main` = le nom local qu'on lui donne dans Terraform
- `cidr_block` = la plage d'adresses IP du réseau
- `tags` = des étiquettes pour identifier la ressource sur AWS

#### Concept 2 — Les variables (`variable`)

Comme dans tout langage de programmation, les variables évitent de répéter des valeurs en dur.

```hcl
# Déclaration de la variable
variable "aws_region" {
  description = "Région AWS à utiliser"
  type        = string
  default     = "eu-west-3"  # Paris
}

# Utilisation
provider "aws" {
  region = var.aws_region
}
```

#### Concept 3 — Les outputs (`output`)

Les outputs sont des "résultats" que Terraform affiche après l'exécution. Utile pour récupérer l'adresse IP d'un serveur qu'AWS vient d'attribuer.

```hcl
output "jenkins_ip" {
  value = aws_instance.jenkins.public_ip
}
```

Après `terraform apply` :
```
Outputs:
jenkins_ip = "15.188.42.101"
```

#### Concept 4 — Les modules

Un module est un dossier contenant des fichiers `.tf` réutilisables. C'est l'équivalent d'une fonction en programmation.

```hcl
# Appeler le module "networking"
module "networking" {
  source = "./modules/networking"
  vpc_cidr = "10.0.0.0/16"
}
```

Le module `networking` contient toute la logique pour créer VPC + subnet + IGW. On l'appelle une seule fois avec des paramètres, sans réécrire tout le code.

#### Concept 5 — Les dépendances

Terraform détecte automatiquement l'ordre de création. Un subnet dépend d'un VPC, donc Terraform crée le VPC en premier.

```hcl
resource "aws_subnet" "public" {
  vpc_id = aws_vpc.main.id  # ← référence le VPC
  # Terraform comprend : créer le VPC avant le subnet
}
```

### 2.4 Ce qu'on a fait dans CloudDelivery — fichier par fichier

#### Arborescence Terraform

```
infra/terraform/
├── providers.tf              ← Déclare AWS comme provider
├── variables.tf              ← Toutes les variables du projet
├── main.tf                   ← Point d'entrée, appelle les 3 modules
├── outputs.tf                ← Affiche les IPs après apply
├── terraform.tfvars.example  ← Exemple de valeurs à renseigner
├── .gitignore                ← Exclut les fichiers secrets et l'état local
└── modules/
    ├── networking/           ← Tout ce qui concerne le réseau
    │   ├── main.tf
    │   ├── variables.tf
    │   └── outputs.tf
    ├── security/             ← Règles de pare-feu (Security Groups)
    │   ├── main.tf
    │   ├── variables.tf
    │   └── outputs.tf
    └── compute/              ← Serveurs EC2
        ├── main.tf
        ├── variables.tf
        └── outputs.tf
```

---

#### `providers.tf` — Déclaration du provider AWS

```hcl
terraform {
  required_version = ">= 1.6.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}
```

**Pourquoi ce fichier ?**
C'est la première chose que Terraform lit. Il lui dit :
- "J'ai besoin de Terraform version 1.6 ou plus récent"
- "J'utilise le plugin AWS version 5.x"
- "Déploie tout dans la région `var.aws_region`" (Paris par défaut)

**Pourquoi pas mettre les credentials AWS ici ?**
Les credentials AWS (access key, secret key) ne sont JAMAIS dans le code. Terraform les lit automatiquement depuis `~/.aws/credentials` (configuré par `aws configure`), ou depuis des variables d'environnement `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY`. Mettre des credentials dans un fichier de code est une faute de sécurité grave.

---

#### `variables.tf` — Les paramètres du déploiement

```hcl
variable "aws_region" {
  description = "AWS region to deploy into"
  type        = string
  default     = "eu-west-3"
}

variable "key_name" {
  description = "Name of the existing AWS EC2 Key Pair for SSH access"
  type        = string
  # Pas de default → l'utilisateur DOIT le fournir
}

variable "your_ip_cidr" {
  description = "Your public IP in CIDR notation (e.g. 1.2.3.4/32)"
  type        = string
}
```

**Pourquoi `eu-west-3` (Paris) ?**
C'est la région AWS la plus proche géographiquement pour un projet français. La latence est plus faible, et les coûts sont similaires.

**Qu'est-ce qu'un CIDR ?**
Un CIDR (Classless Inter-Domain Routing) est une notation pour exprimer une plage d'adresses IP.
- `10.0.0.0/16` = les 65536 adresses de `10.0.0.0` à `10.0.255.255`
- `10.0.1.0/24` = les 256 adresses de `10.0.1.0` à `10.0.1.255`
- `1.2.3.4/32` = exactement l'adresse `1.2.3.4` (une seule IP)

**Pourquoi `your_ip_cidr` avec `/32` ?**
C'est une mesure de sécurité. Le SSH (port 22) et l'interface Jenkins (port 8080) ne sont accessibles QUE depuis votre adresse IP personnelle. Personne d'autre sur internet ne peut s'y connecter. Si vous n'avez pas `your_ip_cidr`, n'importe qui pourrait tenter de forcer l'accès SSH.

---

#### `terraform.tfvars.example` — Template de configuration

```hcl
aws_region        = "eu-west-3"
availability_zone = "eu-west-3a"
key_name          = "clouddelivery-key"    # À remplacer
your_ip_cidr      = "0.0.0.0/32"          # Remplacer par votre IP
instance_type_jenkins = "t3.medium"
instance_type_app     = "t3.medium"
```

**Comment l'utiliser ?**
```bash
cp terraform.tfvars.example terraform.tfvars
# Éditez terraform.tfvars avec vos vraies valeurs
```

**Pourquoi `.example` dans le nom ?**
Le fichier `terraform.tfvars` (sans `.example`) contient vos vraies valeurs (potentiellement votre vraie IP). Il est dans `.gitignore` pour ne jamais être commité sur GitHub. Le fichier `.example` est un modèle vide qui peut être commité sans risque.

---

#### `main.tf` — Le chef d'orchestre

```hcl
module "networking" {
  source = "./modules/networking"
  project_name       = var.project_name
  vpc_cidr           = var.vpc_cidr
  public_subnet_cidr = var.public_subnet_cidr
  availability_zone  = var.availability_zone
}

module "security" {
  source = "./modules/security"
  vpc_id       = module.networking.vpc_id   # ← reçoit l'ID du VPC créé par networking
  your_ip_cidr = var.your_ip_cidr
}

module "compute" {
  source = "./modules/compute"
  subnet_id                 = module.networking.public_subnet_id
  jenkins_security_group_id = module.security.jenkins_sg_id
  app_security_group_id     = module.security.app_sg_id
}
```

**L'ordre de création que Terraform déduit automatiquement :**

```
1. networking  →  crée VPC, subnet, IGW
        ↓
2. security    →  crée Security Groups (a besoin du VPC ID)
        ↓
3. compute     →  crée les EC2 (a besoin du subnet et des SG)
```

Terraform analyse les références (`module.networking.vpc_id`) et comprend seul qu'il doit créer networking avant security. Vous n'avez pas à spécifier l'ordre.

---

#### `modules/networking/main.tf` — Le réseau cloud

Ce module crée 4 ressources AWS interdépendantes :

**1. Le VPC (Virtual Private Cloud)**
```hcl
resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr       # "10.0.0.0/16"
  enable_dns_hostnames = true
}
```

Un VPC est un réseau privé virtuel isolé dans AWS. C'est votre "bulle" réseau : aucun autre compte AWS ne peut accéder à ce qui est dedans par défaut. Tous vos serveurs vivront dans ce VPC.

**2. Le Subnet public**
```hcl
resource "aws_subnet" "public" {
  vpc_id                  = aws_vpc.main.id
  cidr_block              = var.public_subnet_cidr  # "10.0.1.0/24"
  map_public_ip_on_launch = true
}
```

Un subnet est une subdivision du VPC. `map_public_ip_on_launch = true` signifie que chaque EC2 lancé dans ce subnet reçoit automatiquement une adresse IP publique (accessible depuis internet).

**3. L'Internet Gateway (IGW)**
```hcl
resource "aws_internet_gateway" "igw" {
  vpc_id = aws_vpc.main.id
}
```

C'est la "porte" entre votre VPC et internet. Sans IGW, vos serveurs sont isolés et ne peuvent ni recevoir de trafic entrant, ni sortir sur internet.

**4. La Route Table**
```hcl
resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"    # "tout le trafic"
    gateway_id = aws_internet_gateway.igw.id
  }
}
```

Une route table dit "où envoyer les paquets réseau". La règle `0.0.0.0/0 → IGW` signifie : tout trafic qui n'est pas local va vers internet via l'IGW.

**Vue d'ensemble du réseau créé :**

```
Internet
    │
    │
┌───┴──────────────────────────────────────────┐
│  Internet Gateway (IGW)                       │
│                                               │
│  VPC 10.0.0.0/16                             │
│  ┌─────────────────────────────────────────┐ │
│  │  Public Subnet 10.0.1.0/24              │ │
│  │                                          │ │
│  │  ┌─────────────┐  ┌─────────────────┐  │ │
│  │  │  Jenkins     │  │   App Server     │  │ │
│  │  │  EC2         │  │   EC2            │  │ │
│  │  │  10.0.1.10   │  │   10.0.1.11      │  │ │
│  │  └─────────────┘  └─────────────────┘  │ │
│  └─────────────────────────────────────────┘ │
└──────────────────────────────────────────────┘
```

---

#### `modules/security/main.tf` — Les pare-feux (Security Groups)

Un Security Group AWS est un pare-feu virtuel qui contrôle quel trafic est autorisé à entrer (`ingress`) et à sortir (`egress`) d'une instance EC2.

**Security Group pour Jenkins :**
```hcl
resource "aws_security_group" "jenkins" {
  ingress {
    description = "SSH from admin IP"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.your_ip_cidr]   # Votre IP uniquement
  }

  ingress {
    description = "Jenkins Web UI from admin IP"
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = [var.your_ip_cidr]   # Votre IP uniquement
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]       # Tout le trafic sortant autorisé
  }
}
```

**Tableau des règles — qui peut accéder à quoi :**

| Port | Protocole | Source | Raison |
|------|-----------|--------|--------|
| 22 | TCP | Votre IP | SSH pour administrer le serveur |
| 8080 | TCP | Votre IP | Interface web Jenkins |
| Tout | Tous | 0.0.0.0/0 | Sortie libre (télécharger packages, Docker Hub...) |

**Security Group pour l'App Server :**

| Port | Protocole | Source | Raison |
|------|-----------|--------|--------|
| 22 | TCP | Votre IP | SSH pour administrer |
| 80 | TCP | 0.0.0.0/0 | HTTP public |
| 443 | TCP | 0.0.0.0/0 | HTTPS public |
| 8080 | TCP | 0.0.0.0/0 | API Gateway accessible publiquement |
| 8080-8090 | TCP | Jenkins SG | Jenkins peut déployer sur l'App Server |

**Pourquoi deux Security Groups séparés ?**
Le principe du moindre privilège : chaque serveur n'a accès qu'à ce dont il a besoin. Jenkins n'a pas besoin d'être public. L'App Server ne peut pas être administré par n'importe qui.

---

#### `modules/compute/main.tf` — Les serveurs EC2

**L'AMI Ubuntu — sélection dynamique**

Au lieu de coder en dur un ID d'AMI (qui change selon les régions et les versions), on laisse Terraform trouver la dernière version d'Ubuntu 22.04 disponible :

```hcl
data "aws_ami" "ubuntu" {
  most_recent = true
  owners      = ["099720109477"]   # Compte officiel Canonical (éditeur d'Ubuntu)

  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd/ubuntu-jammy-22.04-amd64-server-*"]
  }
}
```

`data` (par opposition à `resource`) ne crée rien : ça lit une information existante sur AWS. Ici, ça lit le catalogue d'AMIs disponibles.

**Les instances EC2**

```hcl
resource "aws_instance" "jenkins" {
  ami                    = data.aws_ami.ubuntu.id   # Ubuntu 22.04 trouvé ci-dessus
  instance_type          = var.instance_type_jenkins # "t3.medium"
  subnet_id              = var.subnet_id             # Dans notre subnet public
  vpc_security_group_ids = [var.jenkins_security_group_id]
  key_name               = var.key_name              # Clé SSH pour se connecter

  root_block_device {
    volume_type = "gp3"
    volume_size = 30      # 30 Go de disque
    encrypted   = true    # Disque chiffré
  }

  user_data = <<-EOF
    #!/bin/bash
    apt-get update -y
    apt-get install -y python3
  EOF
}
```

**Pourquoi `t3.medium` ?**

| Type | vCPU | RAM | Prix/heure (eu-west-3) | Usage |
|------|------|-----|------------------------|-------|
| t3.micro | 2 | 1 Go | ~0.011 € | Trop petit pour Jenkins |
| t3.small | 2 | 2 Go | ~0.022 € | Juste pour les tests légers |
| **t3.medium** | **2** | **4 Go** | **~0.045 €** | **Idéal pour Jenkins + Maven** |
| t3.large | 2 | 8 Go | ~0.090 € | Surdimensionné pour ce projet |

Jenkins avec des builds Maven peut consommer jusqu'à 2-3 Go de RAM. Le `t3.medium` est le bon compromis performance/coût.

**Coût estimé si vous travaillez 4h :**
- 2 × t3.medium × 4h = 2 × 0.045 × 4 = **0.36€**
- Stockage EBS minimal (calculé à l'heure) ≈ 0.02€
- **Total session de 4h : environ 0.40€**

**Pourquoi `encrypted = true` sur le disque ?**
Si le disque est détaché ou le snapshot est partagé par accident, les données restent illisibles sans la clé de chiffrement AWS KMS.

**Pourquoi `user_data` installe Python ?**
Ansible (qu'on verra après) se connecte en SSH et exécute des modules Python sur le serveur distant. Python doit être présent sur le serveur avant qu'Ansible puisse travailler.

---

#### `.gitignore` Terraform

```
.terraform/           ← Plugins téléchargés (gros, inutile de les commiter)
.terraform.lock.hcl   ← Versions exactes des plugins (peut être commité mais optionnel)
terraform.tfstate     ← L'état de l'infra — NE JAMAIS commiter (contient des IDs AWS)
terraform.tfstate.backup
terraform.tfvars      ← Vos valeurs personnelles (IP, clé) — NE JAMAIS commiter
*.tfplan              ← Résultats de terraform plan
crash.log             ← Logs d'erreurs Terraform
```

**Pourquoi ne pas commiter `terraform.tfstate` ?**
Ce fichier contient des informations sensibles (IDs de ressources, parfois des valeurs de variables). Et si deux personnes ont des versions différentes de ce fichier, Terraform va créer des doublons ou supprimer des ressources existantes. En équipe, on utilise un backend distant (S3 + DynamoDB) pour partager ce fichier. Dans ce projet solo, il reste en local.

---

## 3. Ansible — Configurer les serveurs

### 3.1 Qu'est-ce qu'Ansible ?

Ansible est un outil créé par Red Hat (2012) pour **automatiser la configuration des serveurs**. Il se connecte à distance en SSH et exécute des tâches sur les serveurs, sans nécessiter d'installation préalable sur ces serveurs.

**Analogie :** Si Terraform est l'architecte qui construit la maison, Ansible est le décorateur d'intérieur qui installe les meubles, branche l'électricité et configure le wifi.

**La différence avec un script shell**

Un script shell (`install.sh`) avec des `apt install` et `systemctl start` fonctionne... mais :
- Si Docker est déjà installé, le script peut échouer ou réinstaller inutilement
- Aucune gestion des erreurs intelligente
- Pas de parallélisation sur plusieurs serveurs

Ansible résout ça avec le concept d'**idempotence** :

> **Idempotence** : Exécuter la même commande 10 fois = le même résultat que l'exécuter 1 fois. Si Docker est déjà installé, Ansible dit "OK, rien à faire" et passe à la suite.

### 3.2 Comment Ansible fonctionne

```
┌─────────────────────────────────────────────────────────────────┐
│  VOTRE MACHINE (Control Node)                                   │
│                                                                 │
│  ansible-playbook site.yml                                      │
│         │                                                       │
│         │ SSH (port 22)                                         │
│         ▼                                                       │
│  ┌──────────────────────────┐                                   │
│  │  ANSIBLE LIT :           │                                   │
│  │  - inventory.ini         │ → "Quels serveurs ?"              │
│  │  - site.yml              │ → "Quoi faire ?"                  │
│  │  - roles/                │ → "Comment le faire ?"            │
│  └──────────────────────────┘                                   │
└────────────────┬────────────────────────────────────────────────┘
                 │
         SSH (pas de logiciel à installer sur les serveurs)
                 │
    ┌────────────┴────────────┐
    │                         │
    ▼                         ▼
┌──────────────┐       ┌─────────────────┐
│  Jenkins     │       │   App Server    │
│  EC2         │       │   EC2           │
│              │       │                 │
│  installe    │       │  installe       │
│  Docker +    │       │  Docker +       │
│  Jenkins     │       │  docker-compose │
└──────────────┘       └─────────────────┘
```

**Ansible n'a besoin d'aucun agent sur les serveurs distants.** Il utilise juste SSH (déjà disponible sur Ubuntu) et Python (installé par notre `user_data` Terraform). C'est l'un des grands avantages d'Ansible sur des outils comme Chef ou Puppet qui requièrent un agent.

### 3.3 Les concepts clés avec exemples

#### Concept 1 — L'inventaire (`inventory.ini`)

L'inventaire dit à Ansible "quels serveurs tu dois gérer".

```ini
[jenkins]
jenkins-server ansible_host=15.188.42.101 ansible_user=ubuntu

[app]
app-server ansible_host=54.73.18.204 ansible_user=ubuntu

[all:vars]
ansible_ssh_private_key_file=~/.ssh/clouddelivery-key.pem
```

- `[jenkins]` et `[app]` sont des **groupes**. Vous pouvez cibler un groupe entier dans un playbook.
- `ansible_host` = l'adresse IP réelle du serveur
- `ansible_ssh_private_key_file` = le fichier `.pem` AWS pour se connecter en SSH

#### Concept 2 — Le playbook (`site.yml`)

Un playbook est un fichier YAML qui décrit "quoi faire sur quels serveurs".

```yaml
- name: Configurer le serveur Jenkins
  hosts: jenkins        # ← Agit sur le groupe "jenkins" de l'inventaire
  become: yes           # ← Exécute en tant que root (sudo)
  roles:
    - common
    - docker
    - jenkins
```

#### Concept 3 — Les tâches (`tasks`)

Une tâche est une action élémentaire. Ansible fournit des centaines de **modules** prêts à l'emploi.

```yaml
# Installer des packages
- name: Installer Docker
  apt:
    name: docker-ce
    state: present      # "présent" = installé. "absent" = désinstallé.

# Copier un fichier
- name: Copier la config
  copy:
    src: daemon.json
    dest: /etc/docker/daemon.json
    mode: "0644"

# Démarrer un service
- name: Démarrer Docker
  systemd:
    name: docker
    enabled: yes    # Démarrer au boot
    state: started  # Démarrer maintenant

# Exécuter une commande shell (si aucun module ne convient)
- name: Installer Docker Compose
  get_url:
    url: "https://github.com/docker/compose/releases/download/v2.27.0/docker-compose-linux-x86_64"
    dest: /usr/local/bin/docker-compose
    mode: "0755"
```

**Pourquoi les modules plutôt que les commandes shell ?**

```yaml
# MAUVAIS — pas idempotent
- name: Créer le dossier
  command: mkdir /opt/jenkins

# BON — idempotent (ne fait rien si le dossier existe déjà)
- name: Créer le dossier
  file:
    path: /opt/jenkins
    state: directory
```

La commande `mkdir` échoue si le dossier existe déjà. Le module `file` vérifie d'abord, et ne fait rien si c'est déjà dans l'état voulu.

#### Concept 4 — Les rôles (`roles/`)

Un rôle est un ensemble de tâches organisées dans une structure de dossiers standardisée. C'est la façon de modulariser et réutiliser la configuration.

```
roles/
└── docker/
    ├── tasks/
    │   └── main.yml     ← Les tâches d'installation de Docker
    ├── handlers/
    │   └── main.yml     ← Les actions déclenchées par des changements
    ├── templates/
    │   └── daemon.json  ← Fichiers de configuration dynamiques
    └── vars/
        └── main.yml     ← Variables spécifiques au rôle
```

**Avantage :** Si demain vous avez un 3e serveur qui doit avoir Docker, vous ajoutez juste `roles: [docker]` dans son playbook.

#### Concept 5 — Les handlers

Un handler est une action déclenchée uniquement quand quelque chose a changé.

```yaml
tasks:
  - name: Modifier la config Docker
    copy:
      src: daemon.json
      dest: /etc/docker/daemon.json
    notify: Restart Docker   # ← Déclenche le handler SI le fichier a changé

handlers:
  - name: Restart Docker
    systemd:
      name: docker
      state: restarted
```

Si la config Docker n'a pas changé (deuxième exécution d'Ansible), le handler ne redémarre pas Docker inutilement.

#### Concept 6 — Les templates Jinja2

Les templates permettent de générer des fichiers de configuration dynamiquement avec des variables.

```yaml
# Fichier template : jenkins.yaml.j2
jenkins:
  systemMessage: "Déployé par Ansible sur {{ ansible_hostname }}"
  adminEmail: "{{ admin_email }}"
```

```yaml
# Dans une tâche
- name: Déployer la config Jenkins
  template:
    src: jenkins.yaml.j2
    dest: /opt/jenkins/casc/jenkins.yaml
```

Ansible remplace `{{ ansible_hostname }}` par le nom réel du serveur au moment du déploiement.

#### Concept 7 — `group_vars/`

Des variables partagées par tous les serveurs d'un groupe.

```yaml
# group_vars/all.yml — s'applique à TOUS les serveurs
docker_version: "26.1"
jenkins_image: "jenkins/jenkins:lts-jdk17"
app_compose_dir: /opt/clouddelivery
```

Ces variables sont disponibles dans toutes les tâches et templates sans avoir à les redéfinir.

### 3.4 Ce qu'on a fait dans CloudDelivery — fichier par fichier

#### Arborescence Ansible

```
infra/ansible/
├── ansible.cfg                           ← Configuration globale d'Ansible
├── inventory.ini                         ← Liste des serveurs (généré par le script)
├── site.yml                              ← Playbook principal
├── group_vars/
│   └── all.yml                           ← Variables communes à tous les serveurs
└── roles/
    ├── common/
    │   └── tasks/main.yml                ← Packages système + optimisations
    ├── docker/
    │   └── tasks/main.yml                ← Installation Docker CE + Compose
    └── jenkins/
        ├── tasks/main.yml                ← Déploiement Jenkins en container
        └── templates/jenkins.yaml.j2    ← Configuration Jenkins as Code (JCasC)
```

---

#### `ansible.cfg` — Configuration globale

```ini
[defaults]
inventory          = inventory.ini
remote_user        = ubuntu
private_key_file   = ~/.ssh/clouddelivery-key.pem
host_key_checking  = False    ← Ne pas demander de confirmation SSH la première fois
stdout_callback    = yaml     ← Affichage lisible des résultats

[ssh_connection]
pipelining = True             ← Regroupe les commandes SSH → 3× plus rapide
```

**Pourquoi `host_key_checking = False` ?**
Normalement SSH demande "Êtes-vous sûr de vouloir vous connecter à ce nouveau serveur ?" (fingerprint). Pour un serveur qui vient d'être créé par Terraform, c'est normal de ne pas le connaître. On désactive cette vérification pour ne pas bloquer l'automatisation.

**Pourquoi `remote_user = ubuntu` ?**
L'AMI officielle Ubuntu d'AWS crée automatiquement un utilisateur `ubuntu` (et non `root` ou `ec2-user` comme pour Amazon Linux).

---

#### `group_vars/all.yml` — Variables globales

```yaml
docker_version: "26.1"
docker_compose_version: "2.27.0"
jenkins_image: "jenkins/jenkins:lts-jdk17"
jenkins_port: 8080
jenkins_home: /opt/jenkins/home
app_compose_dir: /opt/clouddelivery

jenkins_plugins:
  - git
  - workflow-aggregator      ← Pipeline Groovy
  - docker-workflow          ← Commandes Docker dans les pipelines
  - credentials-binding      ← Secrets injectés dans les pipelines
  - blueocean               ← Interface moderne Jenkins
  - configuration-as-code   ← JCasC plugin
```

**Pourquoi lister les plugins ici ?**
Plutôt que de cliquer dans l'interface Jenkins pour installer chaque plugin, on liste tout dans ce fichier YAML. Ansible les installe automatiquement via `jenkins-plugin-cli`. Reproducible, auditable, versionnable.

---

#### `roles/common/tasks/main.yml` — Préparation du serveur

C'est le rôle de "base" qui s'applique à TOUS les serveurs.

```yaml
- name: Update apt cache
  apt:
    update_cache: yes
    cache_valid_time: 3600   # ← Ne re-télécharge pas les listes si < 1h
```

**Pourquoi `cache_valid_time` ?**
Sur un réseau lent ou limité, `apt update` prend du temps. Si on relance Ansible moins d'une heure après, inutile de re-télécharger les listes.

```yaml
- name: Configurer sysctl pour la production
  sysctl:
    name: "{{ item.name }}"
    value: "{{ item.value }}"
  loop:
    - { name: vm.swappiness,    value: "10" }
    - { name: net.core.somaxconn, value: "65535" }
```

**Qu'est-ce que `sysctl` ?**
Ce sont des paramètres du noyau Linux. `vm.swappiness = 10` signifie "utilise le swap seulement si la RAM est à 90% pleine" (vs 60% par défaut). Idéal pour Docker/JVM qui sont sensibles aux swaps fréquents.

```yaml
- name: Créer swap file (2GB)
  command: fallocate -l 2G /swapfile
  args:
    creates: /swapfile   # ← Ansible ne recrée pas si /swapfile existe déjà
```

**Pourquoi créer un swap ?**
Les instances `t3.medium` ont 4 Go de RAM. Jenkins + Maven peuvent en consommer 2-3 Go lors des builds. Le swap de 2 Go sert de filet de sécurité pour éviter les `OutOfMemoryError`.

---

#### `roles/docker/tasks/main.yml` — Installation Docker

```yaml
- name: Supprimer les anciens packages Docker
  apt:
    name: [docker, docker-engine, docker.io]
    state: absent
    purge: yes
```

Ubuntu 22.04 inclut une version ancienne de Docker dans ses dépôts officiels (`docker.io`). On supprime d'abord ce paquet pour installer la version officielle et récente de Docker, Inc.

```yaml
- name: Ajouter la clé GPG Docker
  apt_key:
    url: https://download.docker.com/linux/ubuntu/gpg
```

**Pourquoi une clé GPG ?**
La clé GPG permet de vérifier que les packages qu'on télécharge viennent vraiment de Docker, Inc. et n'ont pas été altérés. C'est une mesure de sécurité de base pour tout dépôt tiers.

```yaml
- name: Créer le daemon Docker config
  copy:
    dest: /etc/docker/daemon.json
    content: |
      {
        "log-driver": "json-file",
        "log-opts": {
          "max-size": "50m",
          "max-file": "3"
        }
      }
```

**Pourquoi limiter les logs Docker ?**
Sans cette configuration, les logs des containers peuvent remplir le disque. Avec `max-size: 50m` et `max-file: 3`, on garde maximum 150 Mo de logs par container, avec rotation automatique.

```yaml
- name: Ajouter ubuntu au groupe docker
  user:
    name: ubuntu
    groups: docker
    append: yes
```

Sans ça, seul `root` peut exécuter les commandes Docker. Ajouter `ubuntu` au groupe `docker` lui permet de les exécuter sans `sudo`.

---

#### `roles/jenkins/tasks/main.yml` — Déploiement Jenkins

La décision d'utiliser **Jenkins en container Docker** (plutôt que l'installer directement sur le serveur) est importante :

**Avantages du Jenkins containerisé :**
- Pas de conflits avec d'autres logiciels sur le serveur
- Version de Jenkins facilement upgradeable (changer le tag d'image)
- Les données Jenkins sont dans un volume Docker persistent (`/opt/jenkins/home`)
- Si le container plante, `restart_policy: unless-stopped` le relance automatiquement

```yaml
- name: Lancer Jenkins
  docker_container:
    name: jenkins
    image: "jenkins/jenkins:lts-jdk17"
    volumes:
      - "{{ jenkins_home }}:/var/jenkins_home"     ← Persistance des données
      - /var/run/docker.sock:/var/run/docker.sock  ← Docker-in-Docker
      - /usr/local/bin/docker-compose:/usr/local/bin/docker-compose
    env:
      JAVA_OPTS: "-Djenkins.install.runSetupWizard=false -Xmx1g"
      CASC_JENKINS_CONFIG: "/var/jenkins_casc/jenkins.yaml"
```

**Qu'est-ce que `/var/run/docker.sock` ?**
C'est le socket Unix qui permet de communiquer avec le daemon Docker. En le montant dans le container Jenkins, Jenkins peut lancer des commandes Docker (`docker build`, `docker push`) depuis l'intérieur du container, comme si Docker était installé directement. C'est le pattern "Docker-in-Docker" (DinD).

**Pourquoi `-Djenkins.install.runSetupWizard=false` ?**
Par défaut, Jenkins affiche un assistant de configuration au premier démarrage (choisir des plugins, créer un admin...). On désactive cet assistant car notre configuration JCasC fait déjà tout ça automatiquement.

**Pourquoi `-Xmx1g` ?**
Limite la JVM Jenkins à 1 Go de RAM. Sans cette limite, la JVM peut consommer toute la RAM disponible. Avec 4 Go sur le serveur, on garde 3 Go pour les builds Maven et le système.

---

#### `roles/jenkins/templates/jenkins.yaml.j2` — Configuration as Code (JCasC)

JCasC (Jenkins Configuration as Code) est un plugin qui permet de configurer Jenkins entièrement via un fichier YAML, sans passer par l'interface graphique.

```yaml
jenkins:
  systemMessage: "CloudDelivery CI/CD — configuré par Ansible JCasC"

  securityRealm:
    local:
      allowsSignup: false           ← Personne ne peut créer de compte
      users:
        - id: admin
          password: "${JENKINS_ADMIN_PASSWORD}"   ← Lu depuis une variable d'env
```

**Pourquoi `${JENKINS_ADMIN_PASSWORD}` et non un mot de passe en dur ?**
Le fichier `jenkins.yaml` est commité sur GitHub. Si le mot de passe était écrit en clair, n'importe qui lisant le repo pourrait s'y connecter. La notation `${...}` indique à JCasC de lire la variable d'environnement au démarrage.

```yaml
credentials:
  system:
    domainCredentials:
      - credentials:
          - usernamePassword:
              id: dockerhub-credentials
              username: "${DOCKERHUB_USERNAME}"
              password: "${DOCKERHUB_TOKEN}"
          - basicSSHUserPrivateKey:
              id: app-server-ssh
              username: ubuntu
              privateKeySource:
                directEntry:
                  privateKey: "${APP_SERVER_SSH_KEY}"
```

**Qu'est-ce que les credentials Jenkins ?**
Les credentials sont des secrets stockés de façon sécurisée dans Jenkins. Le Jenkinsfile peut les référencer par ID (`dockerhub-credentials`) sans jamais afficher la valeur réelle dans les logs. JCasC permet de les déclarer en YAML au lieu de les saisir manuellement.

---

#### `site.yml` — Le playbook principal

```yaml
- name: Configurer le serveur Jenkins
  hosts: jenkins
  become: yes
  roles:
    - common      ← 1. Packages système
    - docker      ← 2. Docker
    - jenkins     ← 3. Jenkins

- name: Configurer l'App Server
  hosts: app
  become: yes
  roles:
    - common
    - docker
  tasks:
    - name: Cloner le repo
      git:
        repo: "{{ app_repo_url }}"
        dest: "{{ app_compose_dir }}"
```

**Ordre d'exécution sur chaque serveur :**
1. `common` : prépare le serveur (packages, sysctl, swap)
2. `docker` : installe Docker CE et Docker Compose
3. `jenkins` (seulement sur le serveur Jenkins) : lance Jenkins en container

---

## 4. Le Jenkinsfile — La chaîne CI/CD

Le Jenkinsfile est un script Groovy qui définit le pipeline d'intégration et de déploiement continus. Il est stocké à la racine du repo Git : Jenkins le lit automatiquement quand il scanne le projet.

**Vue d'ensemble des 7 étapes :**

```
GitHub Push
     │
     ▼
┌─────────────────────────────────────────────────────────────┐
│  JENKINS PIPELINE                                           │
│                                                             │
│  Stage 1: Checkout          ← Clone le repo                 │
│       │                                                     │
│  Stage 2: Tests (parallèle) ← 4 services en même temps     │
│       │                                                     │
│  Stage 3: Build Images      ← 5 images Docker en parallèle │
│       │                                                     │
│  Stage 4: Trivy Scan        ← Analyse les vulnérabilités   │
│       │                                                     │
│  Stage 5: Push Docker Hub   ← Publie les images            │
│       │                                                     │
│  Stage 6: Deploy (SSH)      ← docker compose up sur App    │
│       │                                                     │
│  Stage 7: Smoke Test        ← Vérifie que l'app répond     │
└─────────────────────────────────────────────────────────────┘
```

**Stage 2 — Tests en parallèle**

```groovy
stage('Unit Tests') {
    parallel {
        stage('Auth Tests') {
            steps {
                dir("Source Code/authentication-service") {
                    sh 'mvn test -B --no-transfer-progress'
                }
            }
            post {
                always {
                    junit 'target/surefire-reports/*.xml'
                }
            }
        }
        stage('Notification Tests') { ... }
        stage('Order Tests') { ... }
        stage('Delivery Tests') { ... }
    }
}
```

Les 4 services de tests s'exécutent **simultanément** plutôt que séquentiellement. Si chaque test prend 3 minutes, en séquentiel c'est 12 minutes, en parallèle c'est 3 minutes.

**Stage 4 — Scan Trivy**

```groovy
sh """
    docker run --rm aquasec/trivy:latest image \
        --exit-code 1 \
        --severity CRITICAL \
        --ignore-unfixed \
        ${IMAGE_NAME}:${IMAGE_TAG}
"""
```

Trivy est un scanner de vulnérabilités (CVE) pour les images Docker. Il analyse les packages système et les dépendances de l'image et retourne les vulnérabilités connues. Avec `--exit-code 1 --severity CRITICAL`, le pipeline échoue si une vulnérabilité CRITIQUE est trouvée. Cela empêche de déployer une image compromise.

**Stage 6 — Déploiement SSH**

```groovy
sh """
    ssh -i $SSH_KEY ubuntu@${APP_SERVER_IP} '
        cd /opt/clouddelivery/Source\\ Code
        export IMAGE_TAG=${IMAGE_TAG}
        docker compose -f docker-compose.prod.yml pull
        docker compose -f docker-compose.prod.yml up -d --remove-orphans
        docker image prune -f
    '
"""
```

Jenkins se connecte en SSH à l'App Server et exécute les commandes pour mettre à jour les containers. `--remove-orphans` supprime les containers qui ne sont plus définis dans le compose. `docker image prune -f` nettoie les anciennes images pour libérer de l'espace disque.

**Stage 7 — Smoke Test**

```groovy
retry(3) {
    sleep(time: 20, unit: 'SECONDS')
    sh """
        curl -sf http://${APP_SERVER_IP}:8080/actuator/health \
            | grep -q '"status":"UP"'
    """
}
```

Un "smoke test" est le test minimal de vérification : "L'application répond-elle à la requête la plus basique ?". Si l'API Gateway répond avec `{"status":"UP"}`, le déploiement est validé. `retry(3)` relance jusqu'à 3 fois (l'application peut prendre quelques secondes à démarrer).

---

## 5. Les scripts — Automatiser les opérations

#### `scripts/generate-inventory.sh`

```bash
JENKINS_IP=$(terraform -chdir="$TERRAFORM_DIR" output -raw jenkins_public_ip)
APP_IP=$(terraform -chdir="$TERRAFORM_DIR" output -raw app_public_ip)

cat > "$INVENTORY_FILE" <<EOF
[jenkins]
jenkins-server ansible_host=${JENKINS_IP}

[app]
app-server ansible_host=${APP_IP}
EOF
```

Après `terraform apply`, les IPs des EC2 sont connues seulement par Terraform. Ce script lit ces IPs depuis les outputs Terraform et génère automatiquement le fichier `inventory.ini` qu'Ansible utilise. Cela évite de copier-coller les IPs manuellement.

#### `scripts/deploy.sh` — Tout en une commande

```bash
terraform -chdir="$TERRAFORM_DIR" init -upgrade
terraform -chdir="$TERRAFORM_DIR" apply -auto-approve
./scripts/generate-inventory.sh
sleep 30   # Attente que les EC2 soient prêtes à accepter SSH
ansible-playbook site.yml -v
```

Ce script enchaîne les 3 phases :
1. Terraform crée les serveurs AWS
2. Le script de génération renseigne les IPs dans l'inventaire
3. Ansible configure les serveurs

**Pourquoi `sleep 30` ?**
Terraform signale qu'une EC2 est "running" dès que l'instance démarre, mais SSH ne devient disponible que ~20-30 secondes plus tard (le système d'exploitation finit de démarrer). Sans ce délai, Ansible tente de se connecter avant que SSH soit prêt et échoue.

#### `scripts/destroy.sh` — Suppression sécurisée

```bash
read -r -p "Type 'yes' to confirm: " CONFIRM
if [[ "$CONFIRM" != "yes" ]]; then
    echo "Aborted."
    exit 0
fi
terraform destroy -auto-approve
```

La demande de confirmation manuelle est intentionnelle. `terraform destroy` supprime TOUT (données comprises). On veut que l'utilisateur y réfléchisse à deux fois. Si la commande était complètement automatique, un appel accidentel en CI/CD pourrait tout effacer.

---

## 6. L'arborescence complète expliquée

```
InfraAsACode/
│
├── Jenkinsfile                          ← Pipeline CI/CD (Jenkins le lit depuis Git)
│
├── DEPLOYMENT.md                        ← Rapport d'architecture (documentation)
├── INFRA-EXPLAINED.md                   ← Ce fichier
│
├── scripts/
│   ├── deploy.sh                        ← Lance Terraform + Ansible d'un coup
│   ├── destroy.sh                       ← Supprime l'infra AWS (avec confirmation)
│   └── generate-inventory.sh           ← Terraform outputs → inventory Ansible
│
├── infra/
│   ├── terraform/
│   │   ├── .gitignore                  ← Exclut tfstate, tfvars, .terraform/
│   │   ├── providers.tf                ← Déclare le provider AWS
│   │   ├── variables.tf                ← Paramètres (région, type EC2, IP, etc.)
│   │   ├── main.tf                     ← Orchestre les 3 modules
│   │   ├── outputs.tf                  ← Affiche les IPs après apply
│   │   ├── terraform.tfvars.example    ← Template à copier (jamais de secrets)
│   │   └── modules/
│   │       ├── networking/
│   │       │   ├── main.tf             ← VPC, Subnet, IGW, Route Table
│   │       │   ├── variables.tf        ← Entrées du module
│   │       │   └── outputs.tf          ← vpc_id, subnet_id (réutilisés par d'autres modules)
│   │       ├── security/
│   │       │   ├── main.tf             ← Security Groups (pare-feu Jenkins + App)
│   │       │   ├── variables.tf
│   │       │   └── outputs.tf          ← jenkins_sg_id, app_sg_id
│   │       └── compute/
│   │           ├── main.tf             ← data AMI Ubuntu + 2 EC2 instances
│   │           ├── variables.tf
│   │           └── outputs.tf          ← jenkins_public_ip, app_public_ip
│   │
│   └── ansible/
│       ├── ansible.cfg                 ← Config globale (user SSH, clé, timeout)
│       ├── inventory.ini               ← IPs des serveurs (généré par le script)
│       ├── site.yml                    ← Playbook principal (qui fait quoi où)
│       ├── group_vars/
│       │   └── all.yml                 ← Variables communes (versions, chemins)
│       └── roles/
│           ├── common/
│           │   └── tasks/main.yml      ← Packages, sysctl, swap
│           ├── docker/
│           │   └── tasks/main.yml      ← Docker CE + Compose v2
│           └── jenkins/
│               ├── tasks/main.yml      ← Container Jenkins + plugins
│               └── templates/
│                   └── jenkins.yaml.j2 ← Configuration Jenkins as Code (JCasC)
│
└── Source Code/
    ├── docker-compose.yaml             ← Dev local (build depuis les sources)
    ├── docker-compose.prod.yml         ← Production (pull depuis Docker Hub)
    ├── authentication-service/
    ├── notification-service/
    ├── order-service/
    ├── delivery-service/
    └── api-gateway/
```

---

## 7. Le flux complet de déploiement

Voici l'enchaînement complet depuis "je veux déployer" jusqu'à "l'application tourne sur AWS" :

```
DÉVELOPPEUR
     │
     │  1. Configure terraform.tfvars (IP, clé SSH)
     │
     ▼
./scripts/deploy.sh
     │
     ├── terraform init
     │       └── Télécharge le plugin AWS
     │
     ├── terraform apply
     │       ├── Crée VPC + Subnet + IGW + Route Table
     │       ├── Crée Security Groups (Jenkins SG + App SG)
     │       ├── Lance EC2 Jenkins (Ubuntu 22.04, t3.medium, 30Go EBS chiffré)
     │       └── Lance EC2 App     (Ubuntu 22.04, t3.medium, 40Go EBS chiffré)
     │
     ├── generate-inventory.sh
     │       └── Lit les IPs depuis terraform output
     │           Écrit inventory.ini avec les vraies IPs
     │
     ├── sleep 30s (EC2 boot)
     │
     └── ansible-playbook site.yml
             │
             ├── Sur Jenkins EC2 :
             │   ├── [common]  : apt update, packages, sysctl, swap 2Go
             │   ├── [docker]  : Docker CE 26.1 + Compose v2.27
             │   └── [jenkins] : Container jenkins:lts-jdk17
             │                   JCasC (admin, credentials, plugins)
             │                   Attente que Jenkins réponde sur :8080
             │                   Installation des 12 plugins listés
             │
             └── Sur App EC2 :
                 ├── [common]  : apt update, packages, sysctl, swap 2Go
                 ├── [docker]  : Docker CE 26.1 + Compose v2.27
                 └── [tasks]   : Clone du repo Git
                                 Création du dossier /opt/clouddelivery


ENSUITE — CI/CD automatique :

DÉVELOPPEUR
     │
     │  git push origin develop
     │
     ▼
GITHUB
     │
     │  GitHub Webhook → Jenkins
     │
     ▼
JENKINS PIPELINE (Jenkinsfile)
     │
     ├── [Stage 1] Checkout + lecture SHA Git
     │
     ├── [Stage 2] Tests unitaires (4 services en parallèle, ~3 min)
     │
     ├── [Stage 3] Build 5 images Docker (parallèle)
     │            auth:a1b2c3d  notification:a1b2c3d  order:a1b2c3d
     │            delivery:a1b2c3d  gateway:a1b2c3d
     │
     ├── [Stage 4] Trivy scan CVE → bloque si CRITICAL
     │
     ├── [Stage 5] docker push → Docker Hub (5 images)
     │
     ├── [Stage 6] SSH → App Server
     │            docker compose pull (télécharge les nouvelles images)
     │            docker compose up -d (rolling update)
     │            docker image prune (nettoyage)
     │
     └── [Stage 7] curl http://APP_IP:8080/actuator/health
                   → {"status":"UP"} ✓ → Pipeline SUCCESS


DÉVELOPPEUR
     │
     │  Fin de session de travail
     │
     ▼
./scripts/destroy.sh
     └── Type 'yes' pour confirmer
         terraform destroy
         → Tout supprimé → 0€ de frais
```

---

## 8. Pourquoi ces choix techniques ?

### Pourquoi Terraform plutôt que CloudFormation (AWS natif) ?

| Critère | Terraform | CloudFormation |
|---------|-----------|----------------|
| Multi-cloud | Oui (AWS, Azure, GCP...) | Non (AWS uniquement) |
| Syntaxe | HCL (lisible) | JSON/YAML verbeux |
| Plan préalable | `terraform plan` | Pas d'équivalent direct |
| Modules réutilisables | Écosystème riche (Registry) | Limité |
| Popularité entreprise | Très élevée | Élevée sur AWS seul |

Terraform est plus universel et plus demandé sur le marché. En cas d'ajout d'un service Azure ou GCP, Terraform supporte tout avec le même outil.

### Pourquoi Ansible plutôt que des scripts shell ?

| Critère | Ansible | Scripts shell |
|---------|---------|---------------|
| Idempotence | Garantie | À coder manuellement |
| Lisibilité | YAML déclaratif | Variable selon l'auteur |
| Gestion d'erreurs | Intégrée | À coder manuellement |
| Parallélisation | Native (`-f 5`) | Complexe à implémenter |
| Templates | Jinja2 intégré | `sed` / `envsubst` |
| Modules disponibles | 3000+ modules officiels | Commandes système brutes |

### Pourquoi des modules Terraform séparés (networking/security/compute) ?

**Principe de séparation des responsabilités :** Chaque module fait une chose et la fait bien.

Si demain on veut ajouter un 3e serveur (base de données dédiée), on modifie uniquement le module `compute` et on lui passe le même `subnet_id` et un nouveau Security Group du module `security`. Le module `networking` n'est pas touché.

Si on veut ajouter un 2e subnet privé, on modifie uniquement `networking`. Les autres modules ne changent pas.

### Pourquoi `t3.medium` et pas `t3.large` ?

Le projet tourne en mode "start → work → destroy". On ne cherche pas la haute performance mais le bon rapport coût/efficacité pour des sessions de 3-4 heures.

```
t3.medium : 4 Go RAM → suffit pour Jenkins + Maven build
t3.large  : 8 Go RAM → dépense 2× pour de la RAM inutilisée
```

Si les builds Maven consomment plus de RAM un jour, on change une seule variable dans `terraform.tfvars` :
```hcl
instance_type_jenkins = "t3.large"
```
Et `terraform apply` redimensionne automatiquement.

### Pourquoi les tests en parallèle dans le Jenkinsfile ?

Le temps de feedback est critique en CI/CD. Si un développeur doit attendre 12 minutes pour savoir si ses tests passent, il va "switcher" sur autre chose et perdre son contexte. En parallélisant les 4 services de tests, on ramène ce feedback à ~3 minutes.

### Pourquoi Trivy dans le pipeline ?

Les images Docker contiennent des couches (OS Ubuntu, JRE Alpine, packages système). Ces packages ont des CVE (vulnérabilités) connues. Sans scan, on peut déployer une application fonctionnelle mais avec une faille de sécurité connue publiquement. Trivy compare les packages de l'image contre la base de données CVE officielle et bloque le déploiement si une vulnérabilité CRITIQUE est trouvée.

---

*Rapport généré le 2026-07-18 pour le projet CloudDelivery — GlioualYassine*
