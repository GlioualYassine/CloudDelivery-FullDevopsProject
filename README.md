# CloudDelivery – Microservices & DevOps Platform

CloudDelivery is an end-to-end **delivery management platform** built as a real-world
DevOps / Cloud-Native project:

- **Backend** : Microservices Spring Boot (Auth, Orders, Deliveries, Payments, Notifications)
- **Realtime** : Node.js WebSocket gateway (positions livreurs, notifications live)
- **Frontend** : Angular (portail client + portail admin)
- **Data** : MongoDB (auth, notifications, events) + PostgreSQL (core métier – option)
- **DevOps / Cloud** :
  - Docker & Docker Compose
  - Kubernetes (local & cloud)
  - Nginx / Ingress
  - CI/CD with Jenkins
  - Infrastructure as Code with Terraform + Ansible

Ce projet est pensé comme un **PFE / portfolio “production-ready”** pour un profil
Java / Angular / DevOps.

---

## 1. Objectifs du projet

- Construire une application **microservices** complète (back + front).
- Mettre en place une **pipeline DevOps** de bout en bout :
  - build → tests → images Docker → push → déploiement Kubernetes.
- Gérer l’infra avec **Infrastructure as Code** :
  - Terraform pour le **provisionnement cloud**
  - Ansible pour la **configuration des machines / outils** (Jenkins, Nginx, etc.).
- Exposer des **cas d’usage concrets** :
  - création et suivi d’une commande
  - tournées de livraison
  - tracking temps réel du livreur
  - notifications asynchrones.

---

## 2. Architecture fonctionnelle

### 2.1. Microservices principaux

- **Auth Service**
  - Gestion des utilisateurs (client, admin, livreur)
  - Authentification JWT
  - Stockage des utilisateurs (MongoDB)

- **Order Service**
  - Gestion des commandes, clients, articles
  - API `/orders` (CRUD commandes, historique client)
  - Stockage dans DB (PostgreSQL ou MongoDB selon choix)

- **Delivery Service**
  - Gestion des livraisons, des tournées et des livreurs
  - Statuts : PENDING, PICKED_UP, ON_ROAD, DELIVERED, FAILED
  - Stockage dans DB (PostgreSQL ou MongoDB)

- **Payment Service**
  - Simulation de paiements
  - Envoi d’évènements vers le broker (paiement accepté/refusé)

- **Notification Service**
  - Consomme les évènements du broker (paiement, livraison, etc.)
  - Sauvegarde les notifications (MongoDB)
  - Peut envoyer email / SMS (mocké pour le projet)

- **Realtime Gateway (Node.js)**
  - Serveur WebSocket / Socket.io
  - Reçoit des **évènements** des microservices (REST / gRPC / Message Broker)
  - Diffuse en temps réel aux clients (Angular) :
    - changement de statut d’une livraison
    - mise à jour de la position d’un livreur
    - nouvelle notification

- **API Gateway**
  - Unique point d’entrée HTTP/HTTPS côté public
  - Route vers les microservices internes (`/auth`, `/orders`, `/deliveries`, `/payments`, `/notifications`)
  - Option: Spring Cloud Gateway ou Nginx / Ingress

- **Spring Cloud Infrastructure**
  - **Config Server** : configuration centralisée (fichiers dans Git)
  - **Eureka Server** : service discovery entre microservices
  - **Zipkin** : tracing distribué des requêtes

### 2.2. Frontends

- **Customer Portal (Angular)**
  - Inscription / login
  - Création de commande
  - Suivi d’une livraison (carte + timeline)
  - Notifications en temps réel via WebSocket

- **Admin Portal (Angular)**
  - Dashboard : nombre de commandes / livraisons, KPIs
  - Gestion des livreurs
  - Vue globale des livraisons
  - Monitoring basique de l’état des services

---

## 3. Architecture logique (vue simplifiée)

```mermaid
flowchart LR
    subgraph Public
      FEClient[Customer Portal<br/>Angular]
      FEAdmin[Admin Portal<br/>Angular]
      APIGW[API Gateway]
      Realtime[Realtime Gateway<br/>Node.js]
    end

    subgraph Private["Private Network / Kubernetes"]
      Auth[Auth Service]
      Order[Order Service]
      Delivery[Delivery Service]
      Payment[Payment Service]
      Notif[Notification Service]
      Broker[(Message Broker)]
      Eureka[Eureka Server]
      Config[Config Server]
      Zipkin[Zipkin Tracing]
      Mongo[(MongoDB)]
      Postgres[(PostgreSQL)]
    end

    FEClient -->|HTTP/REST| APIGW
    FEAdmin  -->|HTTP/REST| APIGW
    FEClient <--> |WebSocket| Realtime

    APIGW -->|/auth| Auth
    APIGW -->|/orders| Order
    APIGW -->|/deliveries| Delivery
    APIGW -->|/payments| Payment
    APIGW -->|/notifications| Notif

    Auth --> Mongo
    Notif --> Mongo
    Order --> Postgres
    Delivery --> Postgres

    Payment -- events --> Broker
    Delivery -- events --> Broker
    Broker --> Notif

    Order --> Eureka
    Delivery --> Eureka
    Auth --> Eureka
    Payment --> Eureka
    Notif --> Eureka
    APIGW --> Eureka

    Order --> Config
    Delivery --> Config
    Auth --> Config
    Payment --> Config
    Notif --> Config

    Order --> Zipkin
    Delivery --> Zipkin
    Auth --> Zipkin
    Payment --> Zipkin
    Notif --> Zipkin

    Payment --> Realtime
    Delivery --> Realtime
