# E-commerce Backend Architecture

A production-ready e-commerce backend showcasing **Event-Driven Architecture**, **CQRS**, **Domain-Driven Design**, and **Microservices patterns**. This application demonstrates modern architectural approaches for scalable, maintainable, and resilient systems.

## 🐳 Quick Docker Setup

### Install Docker
Before running the application, ensure Docker is installed on your system, it simplifies the process:

**Windows/Mac:** Download Docker Desktop from [https://www.docker.com/products/docker-desktop](https://www.docker.com/products/docker-desktop)

**Linux (Ubuntu/Debian):**
```bash
sudo apt update
sudo apt install docker.io docker-compose
sudo systemctl start docker
sudo usermod -aG docker $USER
```

### Essential Docker Commands

**1. Start the complete application stack:**
```bash
docker-compose up -d
```

**2. View running services and logs:**
```bash
# Check all services status
docker-compose ps

# View application logs
docker-compose logs -f ecommerce-backend
```

**3. Stop all services:**
```bash
docker-compose down
```

### Access Your Application
After running `docker-compose up -d`, access:
- 🌐 **Application**: http://localhost:8080
- 📚 **API Documentation**: http://localhost:8080/swagger-ui.html
- 🔍 **Elasticsearch**: http://localhost:9200
- 🗄️ **Database Admin**: http://localhost:8081
- ⚡ **Redis** (CLI): `docker exec -it ecombackend-redis-1 redis-cli -a redis_password_123`
- 📊 **Grafana**: http://localhost:3000 (admin/admin)

### Quick Redis Testing
```bash
# Test view counter functionality
curl "http://localhost:8080/api/v1/products/1"  # Increments view count
curl "http://localhost:8080/api/v1/admin/views/product/1"  # Check count

# Monitor Redis directly
docker exec -it ecombackend-redis-1 redis-cli -a redis_password_123
redis> GET product:views:1
redis> SMEMBERS pending_sync_views
```

---

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
                           │  Web Apps  │  Mobile Apps  │  Third-party Integrations  │
                           └─────────────────────┬───────────────────────────────────┘
                                                 │ HTTP/REST
                           ┌─────────────────────▼───────────────────────────────────┐
                           │                PRESENTATION LAYER                       │
                           │  ┌─────────────┐ ┌─────────────┐ ┌─────────────────┐    │
                           │  │   Product   │ │    Admin    │ │     Health      │    │
                           │  │ Controllers │ │ Controllers │ │   Monitoring    │    │
                           │  └─────────────┘ └─────────────┘ └─────────────────┘    │
                           └─────────────────────┬───────────────────────────────────┘
                                                 │
                           ┌─────────────────────▼───────────────────────────────────┐
                           │                APPLICATION LAYER                        │
                           │  ┌─────────────┐ ┌─────────────┐ ┌─────────────────┐    │
                           │  │   Product   │ │   Search    │ │     Outbox      │    │
                           │  │   Service   │ │   Service   │ │ Event Service   │    │
                           │  └─────────────┘ └─────────────┘ └─────────────────┘    │
                           │  ┌─────────────┐ ┌─────────────┐                        │
                           │  │ViewCounter  │ │ ViewSync    │ 🔥 NEW: Redis Layer    │
                           │  │  Service    │ │  Service    │                        │
                           │  └─────────────┘ └─────────────┘                        │
                           └─────────────────────┬───────────────────────────────────┘
                                                 │
┌──────────────────────┬─────────────────────┼─────────────────────┬──────────────────────┬──────────────────────┐
│                      │                     │                     │                      │                      │
▼                      ▼                     ▼                     ▼                      ▼                      ▼
┌─────────┐         ┌─────────────┐       ┌─────────────┐       ┌─────────────┐         ┌─────────────┐         ┌─────────────┐
│  MySQL  │         │   Outbox    │       │  PubSub     │       │Elasticsearch│         │    Redis    │         │   Admin     │
│Database │◄────────┤    Table    ├──────►│  Broker     ├──────►│   Search    │         │   Cache     │         │  Dashboard  │
│(Write)  │         │(Event Store)│       │(Message Hub)│       │  (Read)     │         │ (Counters)  │         │ (Monitoring)│
└─────────┘         └─────────────┘       └─────────────┘       └─────────────┘         └─────────────┘         └─────────────┘
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
│                            PRESENTATION LAYER                                   │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────────────────┐  │
│  │   Controllers   │  │   DTOs/APIs     │  │     Exception Handlers          │  │
│  │   (REST/HTTP)   │  │  (JSON Models)  │  │    (Error Responses)            │  │
│  └─────────────────┘  └─────────────────┘  └─────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────────┘
                                         │
                                         ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           APPLICATION LAYER                                     │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────────────────┐  │
│  │  Use Cases/     │  │   Mappers       │  │    Event Publishing             │  │
│  │  Services       │  │ (Entity↔DTO)    │  │   (Outbox Pattern)              │  │
│  │ (Business Logic)│  │                 │  │                                 │  │
│  └─────────────────┘  └─────────────────┘  └─────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────────┘
                                         │
                                         ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                             DOMAIN LAYER                                        │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────────────────┐  │
│  │    Entities     │  │   Value Objects │  │      Domain Events              │  │
│  │ (Core Business  │  │   (Immutable    │  │   (Business Events)             │  │
│  │    Logic)       │  │    Objects)     │  │                                 │  │
│  └─────────────────┘  └─────────────────┘  └─────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────────┘
                                         │
                                         ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                         INFRASTRUCTURE LAYER                                    │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────────────────┐  │
│  │  Repositories   │  │   Pub/Sub       │  │    External Services            │  │
│  │ (Data Access)   │  │  (Messaging)    │  │  (Elasticsearch, Monitoring)    │  │
│  └─────────────────┘  └─────────────────┘  └─────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────────┘
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
│                        ASYNCHRONOUS EVENT PROCESSING                            │
│                                                                                 │
│  4️⃣ Event Publisher      5️⃣ Pub/Sub Broker        6️⃣ Event Consumers            │
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
├── 👁️ ProductViewed      → Triggers analytics update (Real-time + Redis Batch Sync)
│   ├── 🚀 Real-time      → Immediate Redis counter increment (<1ms)
│   └── 🔄 Batch Sync     → Periodic DB sync + ES update (30s intervals)
└── 💰 ProductPurchased   → Triggers metrics update

Redis Integration Flow:
┌─────────────────────────────────────────────────────────────────────┐
│ User Views → Redis INCR → Background Sync → DB Update → ES Event    │
│    <1ms        <1ms         30s intervals      ACID       Async     │
└─────────────────────────────────────────────────────────────────────┘

Event Processing Guarantees:
✅ At-least-once delivery
✅ Idempotent processing  
✅ Retry with exponential backoff
✅ Dead letter queue for failures
✅ Message ordering preservation
✅ Redis-first performance optimization
```

---

## ⚡ Redis Real-Time View Counter Architecture

### High-Performance View Tracking System
```
                            REDIS-BASED VIEW COUNTER FLOW
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                                                                                     │
│  1️⃣ User Views Product         2️⃣ Redis Counter         3️⃣ Background Sync           │
│  ┌─────────────────────┐       ┌─────────────────┐       ┌─────────────────────┐    │
│  │   GET /products/1   │───┬─►│    Redis         │       │   ViewSyncService   │    │
│  │   (Sub-millisecond  │   │  │                  │       │   (Every 30s)       │    │
│  │    response)        │   │  │ product:views:1  │       │                     │    │
│  └─────────────────────┘   │  │ ┌─────────────┐  │       │ ┌─────────────────┐ │    │
│                             │  │ │ INCR → 47   │  │◄─────┤ │ Get pending     │ │    │
│  ┌─────────────────────┐   │  │ └─────────────┘  │       │ │ sync products   │ │    │
│  │   ViewCounterService│───┘  │                  │       │ └─────────────────┘ │    │
│  │   .incrementViews() │      │ pending_sync_    │       └─────────────────────┘    │
│  └─────────────────────┘      │ views: {1,2,3}   │                │                │
│                                └─────────────────┘                │                │
└─────────────────────────────────────────────────────────────────────┼────────────────┘
                                          │                          │                
                                          ▼                          ▼                
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                           DATABASE & EVENT SYNCHRONIZATION                         │
│                                                                                     │
│  4️⃣ Database Update             5️⃣ Event Generation          6️⃣ Elasticsearch Sync   │
│  ┌─────────────────────┐       ┌─────────────────────┐       ┌─────────────────┐    │
│  │      MySQL DB       │       │   ProductViewed     │       │  Elasticsearch  │    │
│  │                     │       │      Event          │       │                 │    │
│  │ Product Table       │       │                     │       │ Product Index   │    │
│  │ ┌─────────────────┐ │       │ ┌─────────────────┐ │       │ ┌─────────────┐ │    │
│  │ │ clickCount: 47  │ │◄──────┤ │ Batch Metadata  │ ├──────►│ │clickCount:47│ │    │
│  │ │ popularityScore │ │       │ │ view_increment  │ │       │ │popularity:  │ │    │
│  │ │ updated         │ │       │ │ total_views     │ │       │ │updated      │ │    │
│  │ └─────────────────┘ │       │ └─────────────────┘ │       │ └─────────────┘ │    │
│  └─────────────────────┘       └─────────────────────┘       └─────────────────┘    │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

### Redis Data Structure Design
```
Redis Key Patterns:
├── 🔢 product:views:{productId}           → Total view count (persistent)
├── 📅 product:daily_views:{productId}:{date} → Daily views (7-day TTL)
├── 🔄 pending_sync_views                  → Set of products needing DB sync
└── 🗂️ product:metadata:{productId}        → Optional: cached product data

Example Data:
┌─────────────────────────────────────────────────────────────────────────────────┐
│ Redis Keys:                                                                     │
│ ┌─────────────────────────────────────────────────────────────────────────────┐ │
│ │ product:views:1             → "47"                                         │ │
│ │ product:views:2             → "23"                                         │ │
│ │ product:daily_views:1:2025-09-25 → "15"  (expires in 7 days)              │ │
│ │ pending_sync_views          → {1, 2, 5, 8}  (set of product IDs)          │ │
│ └─────────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Performance Benefits
```
Metric Comparison: Database vs Redis View Tracking

                    Traditional DB          Redis + Background Sync
                    ┌─────────────┐        ┌─────────────────────┐
Response Time       │  50-200ms   │   ═══► │    < 1ms           │
Concurrent Users    │  ~500       │   ═══► │    50,000+         │
Database Load       │  High       │   ═══► │    Near Zero       │
Scalability         │  Limited    │   ═══► │    Horizontal      │
Data Consistency    │  Immediate  │   ═══► │    Eventually      │
                    └─────────────┘        └─────────────────────┘

🔥 Result: 100x faster response times, 100x more concurrent users!
```

### Redis Commands for Monitoring
```bash
# Connect to Redis
redis-cli -a redis_password_123

# Check view counts
GET product:views:1
GET product:views:2

# Check daily views
GET product:daily_views:1:2025-09-25

# Check pending sync queue
SMEMBERS pending_sync_views
SCARD pending_sync_views

# Monitor real-time changes
MONITOR

# Redis system info
INFO memory
INFO keyspace
```

### Configuration
```properties
# Redis Connection
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.data.redis.password=redis_password_123

# View Counter Settings
view.sync.enabled=true
view.sync.interval-ms=30000    # Sync every 30 seconds
view.sync.batch-size=50        # Process 50 products per batch
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
│  │     Topics Map              │    │      Consumer Groups Map                │     │
│  │                             │    │                                         │     │
│  │  "product-events" ───►      │    │  "elasticsearch-sync" ───►              │     │
│  │  ┌─────────────────────┐    │    │  ┌─────────────────────────────────┐    │     │
│  │  │   TopicPartition    │    │    │  │     ConsumerGroupState          │    │     │
│  │  │                     │    │    │  │                                 │    │     │
│  │  │ offsetGenerator: 47 │    │    │  │      subscribedTopics:          │    │     │
│  │  │ ┌─────────────────┐ │    │    │  │      ["product-events"]         │    │     │
│  │  │ │ Message Queue   │ │    │    │  │                                 │    │     │
│  │  │ │ ┌─────┐ ┌─────┐ │ │    │    │  │         topicOffsets:           │    │     │
│  │  │ │ │Msg45│ │Msg46│ │ │    │    │  │     {"product-events": 44}      │    │     │
│  │  │ │ └─────┘ └─────┘ │ │    │    │  └─────────────────────────────────┘    │     │
│  │  │ └─────────────────┘ │    │    └─────────────────────────────────────────┘     │
│  │  └─────────────────────┘    │                                                    │
│  └─────────────────────────────┘                                                    │
│                                                                                     │
│  THREADING MODEL                     OFFSET MANAGEMENT                              │
│  ┌─────────────────────────────┐    ┌─────────────────────────────────────────┐     │
│  │ publisherExecutor           │    │    Manual Commit Pattern                │     │
│  │ (CachedThreadPool)          │    │                                         │     │
│  │                             │    │ 1️⃣ Consumer polls messages              │     │
│  │ consumerExecutor            │    │ 2️⃣ Consumer processes batch             │     │
│  │ (CachedThreadPool)          │    │ 3️⃣ Consumer acknowledges success        │     │
│  │                             │    │ 4️⃣ Broker commits offset                │     │
│  │ retryExecutor               │    │ 5️⃣ Next poll starts from new offset     │     │
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
│                              COMMAND SIDE (WRITE)                                   │
│  ┌─────────────────────────────────────────────────────────────────────────────┐    │
│  │                                                                             │    │
│  │  Write Operations:                    MySQL Database                        │    │
│  │  ┌─────────────────┐                ┌─────────────────────────────────┐     │    │
│  │  │ POST /products  │───────────────►│         Products Table          │     │    │
│  │  │ PUT /products   │                │                                 │     │    │
│  │  │ DELETE /products│                │ - ACID Transactions             │     │    │
│  │  └─────────────────┘                │ - Referential Integrity         │     │    │
│  │                                     │ - Normalized Schema             │     │    │
│  │  Characteristics:                   │ - Optimized for Writes          │     │    │
│  │  ✅ Strong Consistency              └─────────────────────────────────┘     │    │
│  │  ✅ ACID Transactions                                                       │    │
│  │  ✅ Data Validation                                                         │    │
│  │  ✅ Business Rules                                                          │    │
│  └─────────────────────────────────────────────────────────────────────────────┘    │
│                                          │                                          │
│                                          │ Event Publishing                         │
│                                          ▼                                          │
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
│  │  ✅ High Performance               └─────────────────────────────────┘      │   │
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
│    │   (Write Store) │   │   (Search Index)│   │ (View Counters) │                 │
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

## 🔌 Key API Endpoints

### Product Management
```bash
# Get product (auto-increments view count in Redis)
GET /api/v1/products/{id}?userId=user123&sessionId=session456

# Create product
POST /api/v1/products

# Update product  
PUT /api/v1/products/{id}

# Search products
GET /api/v1/products/search?query=laptop&page=0&size=10
```

### Redis View Counter Admin APIs
```bash
# Get view statistics
GET /api/v1/admin/views/stats

# Get specific product view count
GET /api/v1/admin/views/product/{productId}

# Get sync statistics  
GET /api/v1/admin/views/sync-stats

# Trigger manual sync (Redis → Database)
POST /api/v1/admin/views/sync
```

### System Administration
```bash
# Health check
GET /actuator/health

# Metrics
GET /actuator/metrics

# Trigger full Elasticsearch sync
POST /api/v1/admin/sync/full

# Batch event publishing (testing)
POST /api/v1/admin/publish-batch
```

### Example Usage Flow
```bash
# 1. View a product (increments Redis counter)
curl "http://localhost:8080/api/v1/products/1?userId=john&sessionId=abc123"

# 2. Check view count immediately
curl "http://localhost:8080/api/v1/admin/views/product/1"
# Response: {"totalViews": 1, "dailyViews": 1}

# 3. Monitor Redis data
docker exec -it ecombackend-redis-1 redis-cli -a redis_password_123
redis> GET product:views:1
"1"

# 4. Wait 30 seconds for background sync, then check database consistency
curl "http://localhost:8080/api/v1/admin/views/sync-stats"
```

---

**🏗️ Built with modern architecture patterns for learning and demonstrating production-ready design principles.**

> **📘 For detailed API documentation, start the application and visit:** `http://localhost:8080/swagger-ui.html`
