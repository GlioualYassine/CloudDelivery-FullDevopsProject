# Authentication Service

Manages user identity for the CloudDelivery platform. Responsible for registration, authentication, JWT lifecycle (access and refresh tokens), and the password reset flow via a Kafka-driven event pipeline.

## Architecture

```
controller/        REST endpoints
service/           Business logic (UserService, JwtService)
security/          JWT filter and Spring Security configuration
kafka/             Domain event producer (password reset)
model/             MongoDB document entities
repository/        Data access layer
dto/               Request and response models
```

## API

| Method | Path | Description |
|---|---|---|
| `POST` | `/auth/register` | Register a new user account |
| `POST` | `/auth/login` | Authenticate and receive JWT + refresh token |
| `POST` | `/auth/refresh` | Obtain a new access token from a valid refresh token |
| `POST` | `/auth/forgot-password` | Initiate a password reset flow |
| `POST` | `/auth/reset-password` | Complete password reset with the received code |

## Token Strategy

- **Access token**: short-lived (1 hour), signed with HMAC-SHA256, attached to every authenticated request
- **Refresh token**: long-lived (30 days), stored in MongoDB, used exclusively to rotate access tokens
- **Roles**: `ADMIN`, `CLIENT`, `DRIVER` — embedded in the JWT claims

## Password Reset Flow

```
Client                    Auth Service              Kafka                  Notification Service
  |                           |                       |                           |
  |-- POST /forgot-password -->|                       |                           |
  |                           |-- publish event ------>|                           |
  |                           |                       |-- consume event ---------->|
  |                           |                       |                           |-- send email
  |                           |                       |                           |-- persist record
  |-- POST /reset-password --->|                       |                           |
  |<-- 200 OK ----------------|                       |                           |
```

## Events Produced

| Kafka Topic | Event | Trigger |
|---|---|---|
| `password-reset-requests` | `PasswordResetRequestedEvent` | User initiates password reset |

## Configuration

| Property | Description |
|---|---|
| `spring.mongodb.uri` | MongoDB connection string |
| `app.security.jwt.secret` | HMAC secret for JWT signing |
| `app.security.jwt.expiration` | Access token TTL (milliseconds) |
| `app.security.jwt.refresh-expiration` | Refresh token TTL (milliseconds) |
| `app.security.password-reset.expiration-minutes` | Reset code validity window |
| `spring.kafka.bootstrap-servers` | Kafka broker address |
| `app.kafka.password-reset-topic` | Kafka topic for password reset events |

## Running Locally

```bash
docker-compose up -d mongodb kafka zookeeper
mvn spring-boot:run
```

The service starts on port **8081**.

## Tech Stack

- Spring Boot 4.0
- Spring Security
- Spring Data MongoDB
- Spring Kafka
- JJWT 0.11.5
- Lombok
