# JobFlow

JobFlow is a distributed, asynchronous job processing engine built with Spring Boot, Kafka, and Redis, designed to execute background tasks reliably.

## 🎯 The Problem
In modern distributed systems, directly processing heavy tasks (like PDF generation, massive report aggregation, or bulk emails) within a synchronous HTTP request blocks the main thread, causing application timeouts and poor user experience. Additionally, simple background threads are volatile — if a node crashes, the task is lost forever.

## 💡 The Solution
JobFlow solves this by decoupling job ingestion from job processing using an event-driven architecture.
1. The **Job Service** quickly persists the job request and acknowledges the user.
2. An event is published to **Kafka** via the Transactional Outbox pattern.
3. A cluster of **Job Workers** pull jobs from Kafka and process them safely in the background.

## 🏗️ Architecture

```mermaid
graph TD
    User([User / Client])
    
    subgraph Auth
        Login[Auth API<br/>POST /api/v1/auth/login]
        JWT{{JWT Token}}
    end

    subgraph JobFlow Engine
        API[Job Service API]
        DB[(PostgreSQL<br/>+ Flyway Migrations)]
        Outbox[Outbox Poller]
        RetryPoller[Scheduled Retry<br/>Publisher]
        
        subgraph Kafka Cluster - KRaft 3-Node
            K1[Broker 1]
            K2[Broker 2]
            K3[Broker 3]
        end
        
        Redis[(Redis / Redisson<br/>Distributed Lock)]
        
        subgraph Worker Cluster - Horizontally Scalable
            W1[Worker Node 1]
            W2[Worker Node 2]
            W3[Worker Node N]
        end
        
        DLQ[[Dead Letter Queue<br/>job-dlq]]
        InvalidQ[[Invalid Messages<br/>job-events-invalid]]
    end
    
    UI[React 19 Dashboard<br/>+ Login Page]

    User -- "1. POST /api/v1/auth/login" --> Login
    Login -- "Returns JWT" --> JWT
    User -- "2. POST /api/v1/jobs<br/>(Bearer Token)" --> API
    API -- "3. Persist Job + Outbox Event<br/>(@Transactional)" --> DB
    Outbox -- "4. Poll & Publish" --> DB
    Outbox -- "5. Send to job-events" --> K1
    K1 -.-  K2
    K2 -.-  K3
    
    K1 -- "6. Consume" --> W1
    K2 -.- W2
    K3 -.- W3
    
    W1 -- "7a. Acquire Lock" --> Redis
    W1 -- "7b. Optimistic Lock<br/>State Transition" --> DB
    W1 -- "8. Execute Handler<br/>(Strategy Pattern)" --> W1
    W1 -- "9a. SUCCESS → Update DB" --> DB
    W1 -- "9b. ERROR → Schedule<br/>Durable Retry" --> DB
    
    RetryPoller -- "10. Poll due retries" --> DB
    RetryPoller -- "Republish to Kafka" --> K1
    
    W1 -- "Exceeds maxRetries" --> DLQ
    W1 -- "Malformed message" --> InvalidQ
    
    UI -- "GET /api/v1/jobs<br/>GET /api/v1/jobs/stats" --> API
```

### Job State Machine
```mermaid
stateDiagram-v2
    [*] --> CREATED
    CREATED --> PROCESSING
    CREATED --> QUEUED
    QUEUED --> PROCESSING
    PROCESSING --> SUCCESS
    PROCESSING --> ERROR
    PROCESSING --> FAILED
    ERROR --> PROCESSING: Retry via Outbox
    SUCCESS --> [*]
    FAILED --> [*]: Sent to DLQ
```


<img width="1131" height="673" alt="image" src="https://github.com/user-attachments/assets/66e18587-b2cb-4411-a628-f5f68a53c89f" />


## 🛠️ Tech Stack
- **Backend**: Java 25, Spring Boot 4.1.1
- **Database**: PostgreSQL 15 (State, Idempotency & Outbox)
- **Schema Management**: Flyway (versioned SQL migrations)
- **Message Broker**: Apache Kafka 3.7, 3-node KRaft cluster (no Zookeeper) with combined broker+controller roles
- **Distributed Locking**: Redis 7 + Redisson 3.43 (RLock with watchdog auto-renewal)
- **Authentication**: JWT (jjwt 0.12.3) with role-based access control (USER / ADMIN)
- **Frontend**: React 19, Vite 8, Lucide Icons, React Router 7
- **Observability**: Spring Boot Actuator (liveness & readiness probes)
- **Infrastructure**: Docker & Docker Compose *(Note: This repository provides a distributed worker/Kafka topology for portfolio demonstration. For true production high availability, PostgreSQL and Redis would require managed cluster deployments.)*

## 🔐 Authentication & Authorization

JobFlow supports two authentication mechanisms:
1. **Interactive UI Authentication**: JWT-based session tokens with role-based access control (RBAC: `USER` and `ADMIN`).
2. **Machine-to-Machine (M2M) API Key**: Direct service-to-service communication for external microservices and repositories using `X-API-KEY` or `Authorization: ApiKey <KEY>`. Granted `ROLE_ADMIN` and `ROLE_USER` automatically.

### Roles & Machine Access
| Subject | Auth Method | Permissions |
|---|---|---|
| `USER` | JWT `Bearer <token>` | Create jobs, view own jobs |
| `ADMIN` | JWT `Bearer <token>` | Create jobs, view all jobs, access dashboard stats |
| `M2M / Microservice` | Header `X-API-KEY: <key>` | Full programmatic access (create jobs, check status, read stats) |

### Machine-to-Machine (M2M) Integration
Other repositories can interact with JobFlow directly without a prior login call by supplying the pre-shared API key configured via `JOBFLOW_M2M_API_KEY`:
```bash
curl -X POST http://localhost:8080/api/v1/jobs \
  -H "X-API-KEY: jobflow_live_secret_key_12345" \
  -H "Content-Type: application/json" \
  -d '{
    "type": "HTTP_WEBHOOK",
    "payload": {
      "url": "https://api.myrepo.com/webhooks/order-shipped",
      "method": "POST",
      "headers": {
        "Content-Type": "application/json",
        "X-Webhook-Secret": "whsec_abc123"
      },
      "body": {
        "event": "order.completed",
        "orderId": "ord_10293",
        "amount": 99.50
      },
      "rateLimitPerSecond": 10,
      "timeoutSeconds": 15
    },
    "priority": 7,
    "maxRetries": 3
  }'
```

---

## 📡 API Documentation

> **Note:** Endpoints accept either an `X-API-KEY` header (for M2M service calls) or a JWT Bearer token `Authorization: Bearer <token>` (for web clients).

### Authenticate (Interactive Users)
`POST /api/v1/auth/login`
```json
{
  "username": "user",
  "password": "your_password"
}
```
**Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIs..."
}
```

### Create Job
`POST /api/v1/jobs`

**Headers:** `X-API-KEY: <key>` or `Authorization: Bearer <token>`

#### 1. Generic HTTP Webhook (`HTTP_WEBHOOK` or `WEBHOOK`)
Offload outgoing HTTP notifications with per-host distributed rate limiting and automatic exponential backoff:
```json
{
  "type": "HTTP_WEBHOOK",
  "payload": {
    "url": "https://target-service.com/api/callback",
    "method": "POST",
    "headers": {
      "Content-Type": "application/json"
    },
    "body": {
      "eventId": "evt_9982",
      "status": "APPROVED"
    },
    "rateLimitPerSecond": 10,
    "timeoutSeconds": 30
  },
  "priority": 5,
  "maxRetries": 3
}
```
- **2xx**: Succeeded and saved to database with target response body.
- **5xx / 429 / Network timeout**: Retryable. Worker schedules durable retry using exponential backoff (1s → 2s → 4s).
- **4xx (e.g. 400, 404)**: Non-retryable client error. Fails immediately and routes to DLQ without wasteful retries.
- **Distributed Rate Limiting**: Workers coordinate outgoing rate via Redisson token bucket (`ratelimit:webhook:<host>`) across the entire worker cluster.

#### 2. Generic Task
```json
{
  "type": "DATA_SYNC",
  "payload": {
    "source": "orders",
    "destination": "snowflake"
  },
  "priority": 5,
  "maxRetries": 3
}
```

### Get Job Status
`GET /api/v1/jobs/{jobId}`

**Headers:** `X-API-KEY: <key>` or `Authorization: Bearer <token>`

*Users can only view their own jobs. Admins can view all jobs.*

### List All Jobs (Paginated)
`GET /api/v1/jobs?page=0&size=20&sort=createdAt,desc`

**Headers:** `Authorization: Bearer <token>`

*Users see only their own jobs. Admins see all jobs.*

### Get Dashboard Stats
`GET /api/v1/jobs/stats`

**Headers:** `Authorization: Bearer <token>`

*Requires `ADMIN` role.*

**Response:**
```json
{
  "total": 150,
  "queued": 10,
  "processing": 5,
  "successful": 120,
  "failed": 8,
  "dlq": 7
}
```

## 🛡️ Reliability Engineering

### 1. Kafka Design (3-Node KRaft Cluster)
Kafka acts as the central nervous system, running as a **3-node KRaft cluster** with combined broker+controller roles — eliminating the Zookeeper dependency. Topics are configured with replication factor 3 and `min.insync.replicas=2` for durability. By using Consumer Groups, JobFlow naturally load balances tasks across the worker cluster. If a worker goes down, Kafka automatically rebalances the partition to healthy nodes.

### 2. Transactional Outbox Pattern
To solve the classic DB-to-Kafka dual-write problem, the Job Service uses the **Transactional Outbox Pattern**. Instead of publishing to Kafka directly after saving to the database (which could strand jobs if the app crashes between the DB commit and Kafka publish), the API transactionally writes an `outbox_event` to the database alongside the job. A background `OutboxPublisher` poller then reliably sweeps the outbox and publishes the events to Kafka, guaranteeing at-least-once publishing without data loss.

### 3. Exponential Backoff Retry Strategy & Durable Retry Outbox
If a job throws an exception, the worker catches it and calculates an exponential backoff (1s → 2s → 4s). However, rather than scheduling the retry in volatile JVM memory (which would be lost if the worker crashed), it writes a `scheduled_retries` entry into PostgreSQL **transactionally** alongside the job's `ERROR` state. A background `ScheduledRetryPublisher` sweeps the database and reliably pushes due retries back to the Kafka topic. This prevents hammering failing third-party APIs while persisting retry schedules in PostgreSQL so retries survive worker process failures.

### 4. State Idempotency
Because Kafka guarantees at-least-once delivery, duplicates can occur. We enforce *state idempotency* by leveraging JPA Optimistic Locking (`@Version`) on the job state and atomic state transitions in PostgreSQL. Optimistic locking and terminal-state checks prevent duplicate job state progression when multiple workers receive the same job. *(Note: While this protects the internal job state, true business-operation idempotency would require passing a unique idempotency key to downstream external services.)*

### 5. Redis Distributed Locking (Redisson)
To prevent edge cases where two workers might receive identical events simultaneously, workers must acquire a distributed lock before processing. We use **Redisson** for this, which provides a mature locking abstraction (`RLock`). Redisson automatically manages lease renewals (the "watchdog" pattern) for long-running jobs and safely ties the lock to the specific worker thread. If a lock is currently held by another node (e.g. during a partition rebalance), the worker will safely wait to acquire it before proceeding, guaranteeing that duplicate messages do not cause race conditions or premature offset commits.

### 6. Dead Letter Queue (DLQ) & Invalid Message Routing
When a job exceeds its `maxRetries`, it is marked as `FAILED` and published to a special `job-dlq` Kafka topic for manual engineering intervention. Additionally, malformed or unparseable messages are routed to a `job-events-invalid` topic, and events with no registered handler are routed to `job-events-dlq`, ensuring the main consumer is never blocked by poison pills.

### 7. Strategy Pattern for Extensible Task Processing
The worker engine employs the **Strategy Pattern** via a flexible `JobHandler` interface. Instead of hardcoding task logic, developers can create distinct job handlers (`JobHandler` implementations annotated with `@Component`) that the worker dynamically selects based on the event's `type`. Adding a new job type requires only implementing the `JobHandler` interface — zero changes to the consumer or routing logic.

### 8. Synchronous Offsets & Consumer Concurrency
To guarantee zero message loss, the worker processes each task synchronously within the `KafkaListener` thread. This prevents Spring Kafka from committing the consumer offset until the task is definitively completed or securely scheduled for a durable retry. We achieve high throughput and horizontal scalability by running multiple worker replicas and increasing the Kafka topic's partition count, allowing the consumer group to naturally balance the load across the cluster.

### 9. Long-Running Jobs & Consumer Stability
Because Kafka requires consumers to poll periodically, long-running jobs (e.g., executing for 20+ minutes) can trigger a consumer rebalance if they exceed the maximum poll interval. JobFlow explicitly configures `max.poll.interval.ms` to 1 hour (3,600,000 ms) to support expected maximum processing durations without creating unnecessary consumer-group instability.

### 10. Schema Evolution with Flyway
Database schema is managed through **Flyway** versioned SQL migrations, ensuring reproducible, auditable schema changes across all environments. Hibernate is configured with `ddl-auto: validate` (service) / `ddl-auto: none` (worker) to guarantee the schema always matches what Flyway has applied — preventing accidental drift.

## 🚀 How to Run

JobFlow is packaged for startup using Docker Compose.

### 1. Environment Setup
Copy the example environment file and configure your secrets:
```bash
cp .env.example .env
```

Edit `.env` with your values:
```env
# Database
DB_PASSWORD=your_secure_password_here

# Authentication Credentials
JOBFLOW_AUTH_USER_PASSWORD=secure_user_password
JOBFLOW_AUTH_ADMIN_PASSWORD=secure_admin_password

# JWT Settings (secret must be Base64-encoded, at least 256 bits)
JOBFLOW_JWT_SECRET=your_base64_encoded_secure_jwt_secret_here
JOBFLOW_JWT_EXPIRATION_MS=36000000
```

### 2. Start the Infrastructure
Ensure Docker is running, then spin up all services:
```bash
docker-compose up -d --build
```

This starts: PostgreSQL, 3-node Kafka cluster, Redis, Job Service, Job Worker, and the React frontend.

### 3. Access the Dashboard
Open the UI to log in and monitor jobs:
- [http://localhost:5173](http://localhost:5173)

Log in with the credentials you configured in `.env`.

### 4. Scale Workers on the Fly
```bash
docker-compose up -d --scale job-worker=3
```

### 5. Quick API Test
```bash
# Login
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user","password":"your_password"}' | jq -r '.token')

# Create a job with completion webhook callback
curl -X POST http://localhost:8080/api/v1/jobs \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "type": "HTTP_WEBHOOK",
    "callbackUrl": "https://api.myrepo.com/webhooks/job-done",
    "payload": {
      "url": "https://api.external.com/process",
      "method": "POST",
      "body": { "taskId": "tsk_123" }
    },
    "maxRetries": 3
  }'

# Check job status
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/jobs/{jobId}
```

---

## ⚡ Lightweight 1-Node Kafka & HTTPS Reverse Proxy Deployment

For production deployments on memory-constrained VPS instances (or cloud environments where 3 Kafka brokers consume too much RAM), JobFlow includes a **lightweight, production-grade 1-Node Kafka configuration** with an **Nginx HTTPS / Reverse Proxy**:

```
[ Internet Traffic ]
        │
        ├── Port 80 (HTTP)
        └── Port 443 (HTTPS / SSL Termination)
                 │
     [ Nginx Reverse Proxy ]
        ├── /api/*      ──► [ Job Service (8080) ]
        ├── /actuator/* ──► [ Job Service (8080) ]
        └── /*          ──► [ React Frontend (80) ]
```

### 1. Start with 1-Node Kafka + HTTPS
```bash
# 1. (Optional) Generate self-signed SSL certs for local HTTPS testing
bash nginx/generate-certs.sh   # On Linux/macOS
nginx\generate-certs.bat      # On Windows

# 2. Boot up production stack
docker compose -f docker-compose.prod.yml up -d --build
```

### 2. Advantages of `docker-compose.prod.yml`
- **Minimal RAM Footprint**: Replaces the 3-node Kafka cluster with a streamlined 1-node KRaft broker, saving over **1.5 GB of RAM**.
- **SSL / TLS Termination**: Built-in Nginx container terminates HTTPS on port 443 with modern TLSv1.2/1.3 ciphers, HSTS, and gzip compression.
- **Unified Single Ingress**: All internal microservices (`job-service`, `job-worker`, `postgres`, `redis`, `kafka`) remain shielded within the internal Docker bridge network (`jobflow-net`), with only ports 80/443 exposed publicly.
- **Alternative Caddy Support**: A [`caddy/Caddyfile`](file:///l:/My%20stuff/Worker%20SpringBoot/jobflow/caddy/Caddyfile) is also provided for automatic Let's Encrypt certificates.

### 3. Webhook Completion Notifications (`callbackUrl`)
JobFlow allows other systems to receive an asynchronous HTTP POST webhook notification when a task completes or fails:
- Supply `callbackUrl` at the top level or inside `payload` (`callbackUrl` or `callback_url`).
- When the job reaches `SUCCESS`, the worker sends:
  ```json
  {
    "event": "job.completed",
    "jobId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "type": "HTTP_WEBHOOK",
    "status": "SUCCESS",
    "result": { "statusCode": 200 },
    "durationMs": 420,
    "completedAt": "2026-10-22T12:00:00"
  }
  ```
- If retries are exhausted and the job is sent to the Dead Letter Queue (`FAILED`), it sends:
  ```json
  {
    "event": "job.failed",
    "jobId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "type": "HTTP_WEBHOOK",
    "status": "FAILED",
    "errorMessage": "Target returned HTTP 500",
    "retryCount": 3
  }
  ```

## 🎥 Demo
Check out the fully functional dashboard in action, monitoring 100+ async jobs seamlessly recovering from errors and scaling!

![JobFlow Demo](file:///C:/Users/Chetan%20Gupta/.gemini/antigravity-ide/brain/36f22528-0681-46cc-bb22-a0438e0d23f6/jobflow_demo_1791305066076.webp)
