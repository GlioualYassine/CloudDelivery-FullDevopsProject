# Order Service

Manages the order lifecycle for the CloudDelivery platform. Handles order creation, status transitions, and publishes domain events to Kafka when an order is confirmed or cancelled.

## Architecture

```
controller/        REST endpoints
service/           Business logic contracts (interface)
  impl/            Service implementation
kafka/             Domain event producer and event classes
model/             JPA entities (Order, OrderItem, OrderStatus)
repository/        Data access layer (PostgreSQL)
dto/               Request and response models (Java records)
```

Follows a strict layered architecture: Controller → Service interface → Repository. The controller depends only on the service interface, never on the repository directly.

## API

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/orders` | Create a new order with status PENDING |
| `GET` | `/api/v1/orders/{id}` | Retrieve an order by ID |
| `GET` | `/api/v1/orders/client/{email}` | Retrieve all orders for a client, sorted by date descending |
| `PATCH` | `/api/v1/orders/{id}/confirm` | Confirm an order (PENDING → CONFIRMED) |
| `PATCH` | `/api/v1/orders/{id}/cancel` | Cancel an order (PENDING/CONFIRMED → CANCELLED) |

### Create Order — Request Body

```json
{
  "clientEmail": "client@example.com",
  "items": [
    { "productName": "Product A", "quantity": 2, "unitPrice": 15.00 },
    { "productName": "Product B", "quantity": 1, "unitPrice": 35.00 }
  ]
}
```

## Order Lifecycle

```
PENDING ──► CONFIRMED ──► DELIVERED
    │              │
    └──────────────┴──► CANCELLED
```

- An order starts as **PENDING** on creation.
- Confirming a **PENDING** order moves it to **CONFIRMED** and triggers a Kafka event.
- Cancelling a **PENDING** or **CONFIRMED** order moves it to **CANCELLED** and triggers a Kafka event.
- A **DELIVERED** or already **CANCELLED** order cannot be cancelled.

## Events Produced

| Kafka Topic | Event | Trigger |
|---|---|---|
| `order-confirmed` | `OrderConfirmedEvent` | Order confirmed by operator |
| `order-cancelled` | `OrderCancelledEvent` | Order cancelled by client or operator |

## Configuration

| Property | Description | Default |
|---|---|---|
| `spring.datasource.url` | PostgreSQL connection string | `localhost:5432/cld_main_db` |
| `spring.datasource.username` | Database user | `cld_user` |
| `spring.datasource.password` | Database password | `cld_password` |
| `spring.kafka.bootstrap-servers` | Kafka broker address | `localhost:9092` |
| `app.kafka.order-confirmed-topic` | Topic for confirmed orders | `order-confirmed` |
| `app.kafka.order-cancelled-topic` | Topic for cancelled orders | `order-cancelled` |

## Running Locally

Start dependencies:

```bash
docker compose up -d postgres kafka zookeeper
```

Start the service:

```bash
mvn spring-boot:run
```

The service starts on port **8083**.

## Tech Stack

- Spring Boot 4.0
- Spring Data JPA / Hibernate
- PostgreSQL 15
- Spring Kafka
- Bean Validation (Jakarta)
- Lombok
- Java 17 records (DTO layer)
