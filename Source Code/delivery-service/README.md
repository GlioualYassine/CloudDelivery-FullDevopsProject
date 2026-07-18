# Delivery Service

Manages the delivery lifecycle for the CloudDelivery platform. Automatically creates a delivery record when an order is confirmed, tracks driver assignment and GPS status transitions, and publishes a domain event on every status change for downstream consumers (notification-service).

## Architecture

```
controller/     DeliveryController, DriverController
service/        DeliveryService (interface), DriverService (interface)
  impl/         DeliveryServiceImpl, DriverServiceImpl
kafka/          OrderConfirmedConsumer (inbound), DeliveryEventProducer (outbound)
model/          Delivery, Driver, DeliveryStatus
repository/     DeliveryRepository, DriverRepository (PostgreSQL/JPA)
dto/            DeliveryResponse, DriverResponse, DriverRequest, AssignDriverRequest
```

## Delivery Lifecycle

```
[OrderConfirmedEvent received]
        │
        ▼
PENDING_ASSIGNMENT ──► ASSIGNED ──► PICKED_UP ──► ON_ROAD ──► DELIVERED
        │                  │             │             │
        └──────────────────┴─────────────┴─────────────┴──────► FAILED
```

Each transition publishes a `DeliveryStatusUpdatedEvent` to Kafka.

## API

### Deliveries

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/deliveries/{id}` | Get delivery by ID |
| `GET` | `/api/v1/deliveries/order/{orderId}` | Get delivery for a given order |
| `PATCH` | `/api/v1/deliveries/{id}/assign` | Assign an available driver |
| `PATCH` | `/api/v1/deliveries/{id}/pickup` | Driver picked up the package |
| `PATCH` | `/api/v1/deliveries/{id}/on-road` | Driver en route to destination |
| `PATCH` | `/api/v1/deliveries/{id}/complete` | Mark delivery as completed |
| `PATCH` | `/api/v1/deliveries/{id}/fail` | Mark delivery as failed |

### Drivers

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/drivers` | Register a new driver |
| `GET` | `/api/v1/drivers` | List all drivers |
| `GET` | `/api/v1/drivers/available` | List available drivers |
| `GET` | `/api/v1/drivers/{id}` | Get driver by ID |
| `PATCH` | `/api/v1/drivers/{id}/availability?available=true\|false` | Update driver availability |

## Events

### Consumed

| Kafka Topic | Event | Action |
|---|---|---|
| `order-confirmed` | `OrderConfirmedEvent` | Create a delivery in PENDING_ASSIGNMENT state |

### Produced

| Kafka Topic | Event | Trigger |
|---|---|---|
| `delivery-status-updated` | `DeliveryStatusUpdatedEvent` | Any status transition |

## Configuration

| Property | Description | Default |
|---|---|---|
| `spring.datasource.url` | PostgreSQL connection string | `localhost:5432/cld_main_db` |
| `spring.kafka.bootstrap-servers` | Kafka broker address | `localhost:9092` |
| `app.kafka.order-confirmed-topic` | Inbound topic for confirmed orders | `order-confirmed` |
| `app.kafka.delivery-status-topic` | Outbound topic for status updates | `delivery-status-updated` |

## Running Locally

```bash
docker compose up -d postgres kafka zookeeper
mvn spring-boot:run
```

The service starts on port **8084**.

## Tech Stack

- Spring Boot 4.0
- Spring Data JPA / Hibernate
- PostgreSQL 15
- Spring Kafka (consumer + producer)
- Bean Validation (Jakarta)
- Lombok
- Java 17 records (DTO layer)
