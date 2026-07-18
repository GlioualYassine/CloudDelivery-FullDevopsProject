# Cloud Delivery Events

Shared Maven library containing the domain event contracts for the CloudDelivery platform. All microservices that produce or consume the same Kafka event depend on this module as the single source of truth for event schemas.

## Purpose

Eliminates event schema duplication between producer and consumer services. A contract change is made once in this library and propagated to all dependents via a version bump and Maven dependency update.

## Event Catalog

| Class | Package | Producer | Consumer |
|---|---|---|---|
| `PasswordResetRequestedEvent` | `org.example.clouddelivery.event` | authentication-service | notification-service |

## Usage

Install to your local Maven repository before referencing as a dependency:

```bash
mvn install
```

Add the dependency in any microservice `pom.xml`:

```xml
<dependency>
    <groupId>org.example</groupId>
    <artifactId>cloud-delivery-events</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Versioning Policy

This library follows semantic versioning:

- **Major** — breaking change to an existing event field (rename, type change, removal)
- **Minor** — additive change (new optional field, new event class)
- **Patch** — documentation or build script updates only

All consumers must be updated and redeployed before a breaking event change is published in production.

## Tech Stack

- Java 17
- Lombok
