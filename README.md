# E-commerce Backend Architecture

A production-ready e-commerce backend showcasing **Event-Driven Architecture**, **CQRS**, **Domain-Driven Design**, and **Microservices patterns**. This application demonstrates modern architectural approaches for scalable, maintainable, and resilient systems.

## 🏗️ Architectural Overview

This system implements several key architectural patterns working together:

- **🏛️ Domain-Driven Design (DDD)** - Clean layer separation and bounded contexts
- **📡 Event-Driven Architecture** - Loose coupling through events and messaging
- **🔄 CQRS Pattern** - Command Query Responsibility Segregation for optimal performance
- **📦 Transactional Outbox** - Reliable event publishing with guaranteed delivery
- **🎯 Pub-Sub Messaging** - Asynchronous communication with parallel processing
- **⚡ Eventual Consistency** - Scalable data synchronization across services

---

## 🌍 System Architecture - Bird's Eye View

```
                           ┌─────────────────────────────────────────────────────────┐
                           │                    CLIENT LAYER                         │
                           │  Web Apps  │  Mobile Apps  │  Third-party Integrations │
                           └─────────────────────┬───────────────────────────────────┘
                                                 │ HTTP/REST
                           ┌─────────────────────▼───────────────────────────────────┐
                           │                PRESENTATION LAYER                       │
                           │  ┌─────────────┐ ┌─────────────┐ ┌─────────────────┐   │
                           │  │   Product   │ │    Admin    │ │     Health      │   │
                           │  │ Controllers │ │ Controllers │ │   Monitoring    │   │
                           │  └─────────────┘ └─────────────┘ └─────────────────┘   │
                           └─────────────────────┬───────────────────────────────────┘
                                                 │
                           ┌─────────────────────▼───────────────────────────────────┐
                           │                APPLICATION LAYER                        │
                           │  ┌─────────────┐ ┌─────────────┐ ┌─────────────────┐   │
                           │  │   Product   │ │   Search    │ │     Outbox      │   │
                           │  │   Service   │ │   Service   │ │ Event Service   │   │
                           │  └─────────────┘ └─────────────┘ └─────────────────┘   │
                           └─────────────────────┬───────────────────────────────────┘
                                                 │
    ┌──────────────────────┬─────────────────────┼─────────────────────┬──────────────────────┐
    │                      │                     │                     │                      │
    ▼                      ▼                     ▼                     ▼                      ▼
┌─────────┐         ┌─────────────┐       ┌─────────────┐       ┌─────────────┐         ┌─────────────┐
│  MySQL  │         │   Outbox    │       │  PubSub     │       │Elasticsearch│         │   Admin     │
│Database │◄────────┤    Table    ├──────►│  Broker     ├──────►│   Search    │         │  Dashboard  │
│(Write)  │         │(Event Store)│       │(Message Hub)│       │  (Read)     │         │ (Monitoring)│
└─────────┘         └─────────────┘       └─────────────┘       └─────────────┘         └─────────────┘
     │                      │                     │                     │                      │
     │                      │                     ▼                     │                      │
     │                      │              ┌─────────────┐              │                      │
     │                      │              │  Consumer   │              │                      │
     │                      │              │   Groups    │              │                      │
     │                      │              │(Parallel    │              │                      │
     │                      │              │ Workers)    │              │                      │
     │                      │              └─────────────┘              │                      │
     │                      │                                           │                      │
     └──────────────────────┴───────────── Monitoring & Metrics ──────┴──────────────────────┘
                                         (Prometheus, Grafana, Health Checks)
```

---

## 🏛️ Domain-Driven Design Architecture

### Layer Separation
```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                            PRESENTATION LAYER                                  │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────────────────┐ │
│  │   Controllers   │  │   DTOs/APIs     │  │     Exception Handlers          │ │
│  │   (REST/HTTP)   │  │  (JSON Models)  │  │    (Error Responses)            │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────┘
                                         │
                                         ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           APPLICATION LAYER                                    │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────────────────┐ │
│  │  Use Cases/     │  │   Mappers       │  │    Event Publishing             │ │
│  │  Services       │  │ (Entity↔DTO)    │  │   (Outbox Pattern)              │ │
│  │ (Business Logic)│  │                 │  │                                 │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────┘
                                         │
                                         ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                             DOMAIN LAYER                                       │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────────────────┐ │
│  │    Entities     │  │   Value Objects │  │      Domain Events              │ │
│  │ (Core Business  │  │   (Immutable    │  │   (Business Events)             │ │
│  │    Logic)       │  │    Objects)     │  │                                 │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────┘
                                         │
                                         ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                         INFRASTRUCTURE LAYER                                   │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────────────────┐ │
│  │  Repositories   │  │   Pub/Sub       │  │    External Services            │ │
│  │ (Data Access)   │  │  (Messaging)    │  │  (Elasticsearch, Monitoring)    │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Project Structure
```
src/main/java/com/Ecom/backend/
├── 📁 domain/                    # 🏛️ Domain Layer
│   ├── entity/                   # Core business entities
│   └── event/                    # Domain events
├── 📁 application/               # 🎯 Application Layer  
│   ├── dto/                      # Data Transfer Objects
│   ├── mapper/                   # Entity ↔ DTO mapping
│   └── service/                  # Use cases & business logic
├── 📁 infrastructure/            # 🔧 Infrastructure Layer
│   ├── repository/               # Data persistence
│   ├── elasticsearch/            # Search engine integration
│   └── pubsub/                   # Message broker implementation
└── 📁 presentation/              # 🌐 Presentation Layer
    ├── controller/               # REST API controllers
    ├── dto/                      # API request/response models
    └── exception/                # Error handling & responses
```

---

## 🔄 Event-Driven Architecture

### Transactional Outbox Pattern
```
                    API REQUEST PROCESSING FLOW
┌─────────────────────────────────────────────────────────────────────────────────┐
│                                                                                 │
│  1️⃣ HTTP Request        2️⃣ Business Logic        3️⃣ Database Transaction       │
│  ┌─────────────┐       ┌─────────────────┐       ┌─────────────────────────┐    │
│  │   Client    │────►  │  Application    │────►  │      MySQL DB           │    │
│  │  (POST)     │       │    Service      │       │                         │    │
│  └─────────────┘       └─────────────────┘       │ ┌─────────────────────┐ │    │
│                                                  │ │   Product Table     │ │    │
│                                                  │ │   (INSERT/UPDATE)   │ │    │
│                                                  │ └─────────────────────┘ │    │
│                                                  │                         │    │
│                                                  │ ┌─────────────────────┐ │    │
│                                                  │ │   Outbox Table      │ │    │
│                                                  │ │   (Event Storage)   │ │    │
│                                                  │ └─────────────────────┘ │    │
│                                                  └─────────────────────────┘    │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
                                          │
                                          ▼ (ACID Transaction Committed)
┌─────────────────────────────────────────────────────────────────────────────────┐
│                        ASYNCHRONOUS EVENT PROCESSING                           │
│                                                                                 │
│  4️⃣ Event Publisher      5️⃣ Pub/Sub Broker        6️⃣ Event Consumers          │
│  ┌─────────────────┐     ┌─────────────────┐      ┌─────────────────────────┐   │
│  │   Scheduled     │────►│  Message Broker │─────►│    Consumer Groups      │   │
│  │   Processor     │     │   (In-Memory)   │      │                         │   │
│  │  (Every 5sec)   │     │                 │      │ ┌─────────────────────┐ │   │
│  └─────────────────┘     │ ┌─────────────┐ │      │ │ Elasticsearch Sync  │ │   │
│                          │ │ product-    │ │      │ │   (2 Workers)       │ │   │
│                          │ │ events      │ │      │ └─────────────────────┘ │   │
│                          │ │ topic       │ │      │                         │   │
│                          │ └─────────────┘ │      │ ┌─────────────────────┐ │   │
│                          └─────────────────┘      │ │ Analytics Consumer  │ │   │
│                                                   │ │   (Future)          │ │   │
│                                                   │ └─────────────────────┘ │   │
│                                                   └─────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────────┘
                                          │
                                          ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           EVENTUAL CONSISTENCY                                  │
│                                                                                 │
│  7️⃣ Data Synchronization                    8️⃣ Search Index Updated            │
│  ┌─────────────────────────────────┐        ┌─────────────────────────────────┐ │
│  │         MySQL                   │        │        Elasticsearch            │ │
│  │    (Source of Truth)            │   ═══► │      (Search Index)             │ │
│  │                                 │        │                                 │ │
│  │  Product ID: 123                │        │  Product ID: 123                │ │
│  │  Name: "New Product"            │        │  Name: "New Product"            │ │
│  │  Price: $99.99                  │        │  Price: $99.99                  │ │
│  │  Status: ACTIVE                 │        │  Status: ACTIVE                 │ │
│  └─────────────────────────────────┘        └─────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Event Types & Flow
```
Domain Events Generated:
├── 📝 ProductCreated     → Triggers ES indexing
├── 🔄 ProductUpdated     → Triggers ES re-indexing  
├── 🗑️ ProductDeleted     → Triggers ES deletion
├── 👁️ ProductViewed      → Triggers analytics update
└── 💰 ProductPurchased   → Triggers metrics update

Event Processing Guarantees:
✅ At-least-once delivery
✅ Idempotent processing  
✅ Retry with exponential backoff
✅ Dead letter queue for failures
✅ Message ordering preservation
```

---

## 📡 Pub-Sub Message Broker Architecture

### Broker Internal Design (Kafka-Style)
```
                              IN-MEMORY MESSAGE BROKER
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                                                                                     │
│  TOPIC MANAGEMENT                    CONSUMER GROUP MANAGEMENT                      │
│  ┌─────────────────────────────┐    ┌─────────────────────────────────────────┐     │
│  │     Topics Map              │    │      Consumer Groups Map               │     │
│  │                             │    │                                         │     │
│  │  "product-events" ───►      │    │  "elasticsearch-sync" ───►             │     │
│  │  ┌─────────────────────┐    │    │  ┌─────────────────────────────────┐   │     │
│  │  │   TopicPartition    │    │    │  │     ConsumerGroupState          │   │     │
│  │  │                     │    │    │  │                                 │   │     │
│  │  │ offsetGenerator: 47 │    │    │  │ subscribedTopics:               │   │     │
│  │  │ ┌─────────────────┐ │    │    │  │   ["product-events"]            │   │     │
│  │  │ │ Message Queue   │ │    │    │  │                                 │   │     │
│  │  │ │ ┌─────┐ ┌─────┐ │ │    │    │  │ topicOffsets:                   │   │     │
│  │  │ │ │Msg45│ │Msg46│ │ │    │    │  │   {"product-events": 44}        │   │     │
│  │  │ │ └─────┘ └─────┘ │ │    │    │  └─────────────────────────────────┘   │     │
│  │  │ └─────────────────┘ │    │    └─────────────────────────────────────────┘     │
│  │  └─────────────────────┘    │                                                    │
│  └─────────────────────────────┘                                                    │
│                                                                                     │
│  THREADING MODEL                     OFFSET MANAGEMENT                             │
│  ┌─────────────────────────────┐    ┌─────────────────────────────────────────┐     │
│  │ publisherExecutor           │    │    Manual Commit Pattern               │     │
│  │ (CachedThreadPool)          │    │                                         │     │
│  │                             │    │ 1️⃣ Consumer polls messages             │     │
│  │ consumerExecutor            │    │ 2️⃣ Consumer processes batch            │     │
│  │ (CachedThreadPool)          │    │ 3️⃣ Consumer acknowledges success       │     │
│  │                             │    │ 4️⃣ Broker commits offset               │     │
│  │ retryExecutor               │    │ 5️⃣ Next poll starts from new offset   │     │
│  │ (ScheduledThreadPool)       │    │                                         │     │
│  └─────────────────────────────┘    └─────────────────────────────────────────┘     │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

### Consumer Group Parallel Processing
```
                         ELASTICSEARCH SYNC CONSUMER GROUP
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                                                                                     │
│  WORKER DISTRIBUTION                     MESSAGE PROCESSING FLOW                    │
│                                                                                     │
│  ┌─────────────────────────────────┐    ┌─────────────────────────────────────┐     │
│  │        Worker-0                 │    │         Worker-1                    │     │
│  │  ┌─────────────────────────┐    │    │  ┌─────────────────────────────┐    │     │
│  │  │ consumerExecutor        │    │    │  │ consumerExecutor            │    │     │
│  │  │ (CachedThreadPool)      │    │    │  │ (CachedThreadPool)          │    │     │
│  │  │                         │    │    │  │                             │    │     │
│  │  │ retryExecutor           │    │    │  │ retryExecutor               │    │     │
│  │  │ (2 threads)             │    │    │  │ (2 threads)                 │    │     │
│  │  └─────────────────────────┘    │    │  └─────────────────────────────┘    │     │
│  │                                 │    │                                     │     │
│  │  Message Processing:            │    │  Message Processing:                │     │
│  │  ┌─────────────────────────┐    │    │  ┌─────────────────────────────┐    │     │
│  │  │ 1. Poll messages        │    │    │  │ 1. Poll messages            │    │     │
│  │  │ 2. handleProductEvent() │    │    │  │ 2. handleProductEvent()     │    │     │
│  │  │ 3. Update Elasticsearch │    │    │  │ 3. Update Elasticsearch     │    │     │
│  │  │ 4. Acknowledge success  │    │    │  │ 4. Acknowledge success      │    │     │
│  │  └─────────────────────────┘    │    │  └─────────────────────────────┘    │     │
│  └─────────────────────────────────┘    └─────────────────────────────────────┘     │
│                    │                                        │                       │
│                    ▼                                        ▼                       │
│                 Message 1                               Message 2                   │
│              (ProductCreated)                        (ProductUpdated)               │
│                    │                                        │                       │
│                    ▼                                        ▼                       │
│           ┌─────────────────┐                     ┌─────────────────┐               │
│           │  Elasticsearch  │◄────────────────────┤  Elasticsearch  │               │
│           │     Index       │      Parallel       │     Index       │               │
│           │    Update       │      Updates        │    Update       │               │
│           └─────────────────┘                     └─────────────────┘               │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 🎯 CQRS Implementation

### Command & Query Separation
```
                                  CQRS ARCHITECTURE
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                                                                                     │
│                              COMMAND SIDE (WRITE)                                  │
│  ┌─────────────────────────────────────────────────────────────────────────────┐   │
│  │                                                                             │   │
│  │  Write Operations:                    MySQL Database                        │   │
│  │  ┌─────────────────┐                ┌─────────────────────────────────┐     │   │
│  │  │ POST /products  │───────────────►│         Products Table          │     │   │
│  │  │ PUT /products   │                │                                 │     │   │
│  │  │ DELETE /products│                │ - ACID Transactions             │     │   │
│  │  └─────────────────┘                │ - Referential Integrity         │     │   │
│  │                                     │ - Normalized Schema             │     │   │
│  │  Characteristics:                   │ - Optimized for Writes          │     │   │
│  │  ✅ Strong Consistency              └─────────────────────────────────┘     │   │
│  │  ✅ ACID Transactions                                                        │   │
│  │  ✅ Data Validation                                                          │   │
│  │  ✅ Business Rules                                                           │   │
│  └─────────────────────────────────────────────────────────────────────────────┘   │
│                                          │                                         │
│                                          │ Event Publishing                        │
│                                          ▼                                         │
│                              ┌─────────────────────┐                              │
│                              │    Outbox Events    │                              │
│                              │   (Event Bridge)    │                              │
│                              │                     │                              │
│                              │ ┌─────────────────┐ │                              │
│                              │ │ ProductCreated  │ │                              │
│                              │ │ ProductUpdated  │ │                              │
│                              │ │ ProductDeleted  │ │                              │
│                              │ └─────────────────┘ │                              │
│                              └─────────────────────┘                              │
│                                          │                                         │
│                                          │ Pub/Sub System                          │
│                                          ▼                                         │
│                               QUERY SIDE (READ)                                   │
│  ┌─────────────────────────────────────────────────────────────────────────────┐   │
│  │                                                                             │   │
│  │  Read Operations:                   Elasticsearch                           │   │
│  │  ┌─────────────────┐               ┌─────────────────────────────────┐      │   │
│  │  │ GET /products   │──────────────►│        Search Index             │      │   │
│  │  │ GET /search     │               │                                 │      │   │
│  │  │ GET /related    │               │ - Full-text Search              │      │   │
│  │  └─────────────────┘               │ - Aggregations & Facets         │      │   │
│  │                                    │ - Denormalized Documents        │      │   │
│  │  Characteristics:                  │ - Optimized for Reads           │      │   │
│  │  ✅ High Performance                └─────────────────────────────────┘      │   │
│  │  ✅ Complex Queries                                                          │   │
│  │  ✅ Horizontal Scaling                                                       │   │
│  │  ✅ Eventual Consistency                                                     │   │
│  └─────────────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 🔧 Technology Stack & Integration

### Infrastructure Components
```
                              TECHNOLOGY STACK OVERVIEW
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                                                                                     │
│  DEVELOPMENT STACK                      RUNTIME ENVIRONMENT                        │
│  ┌─────────────────────────────────┐    ┌─────────────────────────────────────┐     │
│  │                                 │    │                                     │     │
│  │  ☕ Java 17 (LTS)              │    │  🐳 Docker & Docker Compose       │     │
│  │  🍃 Spring Boot 3.5.6           │    │  ☸️ Kubernetes Ready              │     │
│  │  📊 Spring Data JPA             │    │  🔧 HikariCP Connection Pool      │     │
│  │  🔍 Spring Data Elasticsearch   │    │  ⚡ Native Compilation Support    │     │
│  │  🗺️ MapStruct (Type-safe)       │    │                                     │     │
│  │  📝 Lombok (Boilerplate)        │    │                                     │     │
│  └─────────────────────────────────┘    └─────────────────────────────────────┘     │
│                                                                                     │
│  DATA STORAGE                           MESSAGING & EVENTS                         │
│  ┌─────────────────────────────────┐    ┌─────────────────────────────────────┐     │
│  │                                 │    │                                     │     │
│  │  🗄️ MySQL 8.0 (Primary DB)      │    │  📡 Custom Pub/Sub (Kafka-ready)   │     │
│  │  🔍 Elasticsearch 8.11 (Search) │    │  📦 Transactional Outbox           │     │
│  │  🔴 Redis (Cache - Optional)     │    │  🔄 Parallel Message Processing    │     │
│  │  💾 In-Memory H2 (Testing)       │    │  ♻️ Retry Logic & Dead Letters     │     │
│  │                                 │    │  🎯 Consumer Groups                 │     │
│  └─────────────────────────────────┘    └─────────────────────────────────────┘     │
│                                                                                     │
│  MONITORING & OBSERVABILITY            API & DOCUMENTATION                         │
│  ┌─────────────────────────────────┐    ┌─────────────────────────────────────┐     │
│  │                                 │    │                                     │     │
│  │  📈 Prometheus (Metrics)        │    │  📚 OpenAPI 3.0 (Swagger)          │     │
│  │  📊 Grafana (Dashboards)        │    │  🔗 REST APIs (JSON)               │     │
│  │  🏥 Spring Actuator (Health)    │    │  🌐 CORS Support                   │     │
│  │  📝 Structured Logging          │    │  🔒 Security Headers               │     │
│  │  🔍 Distributed Tracing Ready   │    │  ⚡ Async Processing               │     │
│  └─────────────────────────────────┘    └─────────────────────────────────────┘     │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 🚀 Getting Started

### Quick Start
```bash
# Clone repository
git clone <repository-url>
cd EcomBackend

# Start all services with Docker
docker-compose up -d

# Access the application
# 🌐 Application: http://localhost:8080
# 📚 API Docs: http://localhost:8080/swagger-ui.html
# 🔍 Elasticsearch: http://localhost:9200
# 🗄️ Database Admin: http://localhost:8081
# 📊 Grafana: http://localhost:3000 (admin/admin)
```

### Service URLs
```
🎯 Application Services:
├── 🌐 Main Application: http://localhost:8080
├── 📚 API Documentation (Swagger): http://localhost:8080/swagger-ui.html
├── 🏥 Health Checks: http://localhost:8080/actuator/health
└── 📊 Metrics: http://localhost:8080/actuator/metrics

🔧 Infrastructure Services:
├── 🗄️ MySQL Database Admin: http://localhost:8081
├── 🔍 Elasticsearch: http://localhost:9200
├── 🔴 Redis: localhost:6379
├── 📈 Prometheus: http://localhost:9090
└── 📊 Grafana: http://localhost:3000

📡 API Endpoints:
├── 📦 Products: /api/v1/products/*
├── 🔍 Search: /api/v1/products/search
├── 👑 Admin: /api/v1/admin/*
└── 🏥 Health: /api/v1/health/*
```

> **💡 Tip:** All API endpoints and schemas are documented in Swagger UI. Access it at `http://localhost:8080/swagger-ui.html` after starting the application.

---

## 🎯 Key Architecture Benefits

### ✅ **Scalability**
- Horizontal scaling through stateless design
- Independent scaling of read/write operations
- Parallel message processing with worker pools
- Event-driven architecture reduces coupling

### ✅ **Resilience** 
- Circuit breaker patterns for external services
- Retry logic with exponential backoff
- Dead letter queues for failed messages
- Health checks and monitoring

### ✅ **Performance**
- CQRS optimization for reads and writes
- Elasticsearch for fast search operations
- Connection pooling and caching
- Async processing doesn't block APIs

### ✅ **Maintainability**
- Clean architecture with layer separation
- Domain-driven design principles
- Comprehensive testing strategy
- Type-safe mappings and validations

### ✅ **Observability**
- Structured logging with correlation IDs
- Metrics collection and visualization
- Health checks and monitoring endpoints
- Event audit trails

---

## 📈 Production Deployment Architecture

```
                               PRODUCTION DEPLOYMENT
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                                                                                     │
│                               🌍 INTERNET                                          │
│                                    │                                               │
│                                    ▼                                               │
│                          ┌─────────────────┐                                       │
│                          │  Load Balancer  │                                       │
│                          │   (nginx/ALB)   │                                       │
│                          └─────────────────┘                                       │
│                                    │                                               │
│              ┌─────────────────────┼─────────────────────┐                         │
│              │                     │                     │                         │
│              ▼                     ▼                     ▼                         │
│    ┌─────────────────┐   ┌─────────────────┐   ┌─────────────────┐                 │
│    │   App Instance  │   │   App Instance  │   │   App Instance  │                 │
│    │      (Pod 1)    │   │      (Pod 2)    │   │      (Pod 3)    │                 │
│    └─────────────────┘   └─────────────────┘   └─────────────────┘                 │
│              │                     │                     │                         │
│              └─────────────────────┼─────────────────────┘                         │
│                                    │                                               │
│                                    ▼                                               │
│                          ┌─────────────────┐                                       │
│                          │  Message Broker │                                       │
│                          │ (Apache Kafka)  │                                       │
│                          └─────────────────┘                                       │
│                                    │                                               │
│              ┌─────────────────────┼─────────────────────┐                         │
│              │                     │                     │                         │
│              ▼                     ▼                     ▼                         │
│    ┌─────────────────┐   ┌─────────────────┐   ┌─────────────────┐                 │
│    │   MySQL DB      │   │  Elasticsearch  │   │   Redis Cache   │                 │
│    │   (Cluster)     │   │   (Cluster)     │   │   (Cluster)     │                 │
│    └─────────────────┘   └─────────────────┘   └─────────────────┘                 │
│                                                                                     │
│  MONITORING STACK                                                                  │
│  ┌─────────────────┐   ┌─────────────────┐   ┌─────────────────┐                   │
│  │   Prometheus    │   │    Grafana      │   │   ELK Stack     │                   │
│  │   (Metrics)     │   │  (Dashboards)   │   │   (Logging)     │                   │
│  └─────────────────┘   └─────────────────┘   └─────────────────┘                   │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 🤝 Contributing & Development

### Code Quality Standards
- **SOLID Principles** implementation
- **Clean Architecture** patterns  
- **Test-Driven Development** (TDD)
- **Domain-Driven Design** (DDD)
- **Comprehensive Testing** strategy

### Testing Strategy
```bash
# Run all tests
./mvnw test

# Architecture tests
./mvnw test -Dtest="**/*ArchTest"

# Integration tests  
./mvnw test -Dtest="**/*IntegrationTest"

# Performance tests
./mvnw test -Dtest="**/*PerformanceTest"
```

---

**🏗️ Built with modern architecture patterns for learning and demonstrating production-ready design principles.**

> **📘 For detailed API documentation, start the application and visit:** `http://localhost:8080/swagger-ui.html`
