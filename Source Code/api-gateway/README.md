# API Gateway

Single entry point for the CloudDelivery platform. All client requests pass through port 8080 and are routed to the appropriate microservice based on the request path.

## Architecture

```
Client (Angular / Postman)
         |
         | :8080
         v
     API Gateway
         |
   ┌─────┼──────────────────┐
   |     |                  |
   v     v                  v
Auth  Notification    Order / Delivery
:8081   :8082          :8083 / :8084
```

## Route Table

| Path prefix | Upstream service | Port |
|---|---|---|
| `/auth/**` | authentication-service | 8081 |
| `/api/v1/notifications/**` | notification-service | 8082 |
| `/api/v1/orders/**` | order-service | 8083 |
| `/api/v1/deliveries/**` | delivery-service | 8084 |
| `/api/v1/drivers/**` | delivery-service | 8084 |

## CORS

Requests from `http://localhost:4200` (Customer Portal) and `http://localhost:4300` (Admin Portal) are allowed for all methods and headers.

## Configuration

Upstream URLs are configurable via environment variables so they resolve correctly both locally and inside Docker:

| Variable | Default (local) | Docker override |
|---|---|---|
| `AUTHENTICATION_SERVICE_URL` | `http://localhost:8081` | `http://authentication-service:8081` |
| `NOTIFICATION_SERVICE_URL` | `http://localhost:8082` | `http://notification-service:8082` |
| `ORDER_SERVICE_URL` | `http://localhost:8083` | `http://order-service:8083` |
| `DELIVERY_SERVICE_URL` | `http://localhost:8084` | `http://delivery-service:8084` |

## Running Locally

```bash
mvn spring-boot:run
```

The gateway starts on port **8080**. All upstream services must be running on their respective ports.

## Health

```
GET /actuator/health
```

## Tech Stack

- Spring Boot 4.0
- Spring Cloud Gateway 4.2.0 (reactive, WebFlux-based)
- Java 17
