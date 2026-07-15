# Notification Service

Handles outbound notifications for the CloudDelivery platform. Consumes domain events from Kafka, dispatches transactional emails via SMTP, and maintains a persistent notification history per user in MongoDB.

## Architecture

```
controller/        REST API (query and read management)
service/           Business logic contracts (interface)
  impl/            Service implementations
kafka/             Kafka event consumers
dto/               API response models
model/             MongoDB document entities
repository/        Data access layer
```

Design follows the **layered architecture** pattern with strict dependency direction: Controller -> Service interface -> Repository. The service layer is exposed through an interface to decouple the REST layer from any specific implementation.

## Notification Types

| Type | Trigger |
|---|---|
| `PASSWORD_RESET` | User requests a password reset |
| `ORDER_CONFIRMED` | Order placed and confirmed |
| `ORDER_CANCELLED` | Order cancelled by client or admin |
| `DELIVERY_ASSIGNED` | A driver is assigned to a delivery |
| `DELIVERY_PICKED_UP` | Driver picks up the package |
| `DELIVERY_ON_ROAD` | Driver en route to the destination |
| `DELIVERY_COMPLETED` | Order delivered successfully |
| `DELIVERY_FAILED` | Delivery attempt failed |

## API

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/notifications/user/{email}` | Retrieve all notifications for a user, sorted by date descending |
| `PATCH` | `/api/v1/notifications/{id}/read` | Mark a notification as read |

## Events Consumed

| Kafka Topic | Event | Action |
|---|---|---|
| `password-reset-requests` | `PasswordResetRequestedEvent` | Send reset code by email, persist notification record |

## Configuration

| Property | Description | Default |
|---|---|---|
| `spring.data.mongodb.uri` | MongoDB connection string | `localhost:27017` |
| `spring.kafka.bootstrap-servers` | Kafka broker address | `localhost:9092` |
| `app.kafka.password-reset-topic` | Kafka topic for password reset events | `password-reset-requests` |
| `MAIL_USERNAME` | SMTP account (environment variable) | — |
| `MAIL_PASSWORD` | SMTP password (environment variable) | — |
| `spring.mail.host` | SMTP server hostname | `smtp.gmail.com` |
| `spring.mail.port` | SMTP port (STARTTLS) | `587` |

## Running Locally

Start dependencies from the root `docker-compose.yaml`:

```bash
docker-compose up -d mongodb kafka zookeeper
```

Set required environment variables and start the service:

```bash
export MAIL_USERNAME=your-email@gmail.com
export MAIL_PASSWORD=your-app-password
mvn spring-boot:run
```

The service starts on port **8082**.

## Tech Stack

- Spring Boot 4.0
- Spring Data MongoDB 
- Spring Kafka
- Spring Mail (JavaMailSender / SMTP)
- Lombok
- Java 17 records (DTO layer)
