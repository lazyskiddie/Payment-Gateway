# Event-Driven Payment Processing Engine

> A microservice-based payment processing system designed to handle high-throughput, asynchronous transactions securely. This project demonstrates enterprise-grade architecture patterns, specifically tailored for scalable financial networks and payment gateways.

---

## ️ Architecture Overview

The system consists of two decoupled Spring Boot microservices communicating asynchronously via **Apache Kafka**.

### 1. Payment Gateway (Service A - Producer)
* **API Entry Point:** Exposes a RESTful API (`POST /api/v1/payments`).
* **Validation:** Handles immediate request validation.
* **Security:** Enforces database-level idempotency to prevent duplicate transaction submissions.
* **Event Publishing:** Saves a `PENDING` state and publishes a `PaymentEvent` to Kafka.
* **High Availability:** Returns a non-blocking `202 Accepted` to the client.

### 2. Core Processor (Service B - Consumer)
* **Event Listener:** Acts as an asynchronous Kafka consumer.
* **Safety Checks:** Implements consumer-side idempotency checks to handle "at-least-once" delivery semantics safely.
* **Business Logic:** Simulates downstream banking transaction processing.
* **State Management:** Updates the database transaction state to `COMPLETED` or `FAILED`.

---

## Tech Stack

* **Backend:** Java 17, Spring Boot 3.x, Spring Data JPA, Spring Web
* **Messaging:** Apache Kafka, Zookeeper, Spring for Apache Kafka
* **Database:** PostgreSQL 15
* **Infrastructure:** Docker, Docker Compose
* **Testing:** JUnit 5, Mockito, Testcontainers

---

## Key Engineering Decisions

*  **Dual-Tier Idempotency:** Idempotency keys are passed via HTTP headers to guarantee that network retries do not result in double charges. This is enforced via database unique constraints (Gateway) and state-validation (Consumer).
*  **Financial Data Precision:** Strictly utilizing `BigDecimal` rather than `Double` or `Float` to prevent catastrophic floating-point rounding errors during financial calculations.
*  **Resilience & Backpressure:** The REST Gateway is completely insulated from downstream processing bottlenecks. If the Core Processor experiences downtime, the Gateway continues to accept traffic, queueing messages in Kafka with zero data loss.
*  **Observability Readiness:** Prepared for distributed tracing by passing correlation IDs, ensuring full visibility of a transaction's lifecycle across microservices.

---

##  Getting Started

### Prerequisites
* Java 17 or higher
* Maven 3.8+
* Docker and Docker Compose

### 1. Start the Infrastructure
Navigate to the root directory and start the PostgreSQL database and Kafka broker using Docker Compose. *(This exposes PostgreSQL on port 5432 and Kafka on port 9092.)*

```bash
docker-compose up -d
```

### 2. Run the Microservices
You will need two separate terminal windows to run both services concurrently.

**Start the Payment Gateway:** *(Runs on http://localhost:8080)*
```bash
cd payment-gateway
mvn spring-boot:run
```

**Start the Core Processor:** *(Runs on http://localhost:8081)*
```bash
cd payment-processor
mvn spring-boot:run
```

---

##  Testing the API

Once both services are running, you can simulate a payment request using `cURL`.

**The Request:**
```bash
curl -X POST http://localhost:8080/api/v1/payments \
-H "Content-Type: application/json" \
-H "Idempotency-Key: uuid-12345-abcde" \
-d '{
"sourceAccount": "ACC-9999",
"destinationAccount": "ACC-1111",
"amount": 250.50
}'
```

**The Expected Response:**
```http
HTTP/1.1 202 Accepted
Payment accepted for processing
```

###  Verify the Architecture:
1. Check the **Payment Gateway** console logs to see the `PENDING` transaction saved and the Kafka event published.
2. Check the **Core Processor** console logs to see the event instantly consumed, processed, and marked as `COMPLETED`.
3.  **The Duplicate Test:** Try sending the exact same cURL request again. You will receive an error, demonstrating that the idempotency constraint successfully blocked a duplicate charge!
