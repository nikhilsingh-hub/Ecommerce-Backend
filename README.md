# E-commerce Backend API

A production-minded E-commerce backend application demonstrating event-driven architecture, CQRS pattern, and microservices design principles.

## 🏗️ Architecture Overview

This application showcases a modern, scalable e-commerce backend with the following key architectural patterns:

- **Event-Driven Architecture** with transactional outbox pattern
- **CQRS (Command Query Responsibility Segregation)** with separate read/write models
- **Domain-Driven Design (DDD)** with proper layer separation
- **Microservices-ready** design with replaceable components
- **Eventual Consistency** between MySQL and Elasticsearch
- **Pub/Sub Messaging** with batch processing and parallel consumers

### System Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   REST API      │    │   GraphQL       │    │   Admin API     │
│   (Products)    │    │   (Optional)    │    │   (Batch Ops)   │
└─────────┬───────┘    └─────────┬───────┘    └─────────┬───────┘
          │                      │                      │
          └──────────────────────┼──────────────────────┘
                                 │
                    ┌─────────────▼─────────────┐
                    │     Application Layer     │
                    │  (Services, Use Cases)    │
                    └─────────────┬─────────────┘
                                  │
          ┌───────────────────────┼───────────────────────┐
          │                       │                       │
          ▼                       ▼                       ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   MySQL DB      │    │  Outbox Events  │    │  Elasticsearch  │
│ (Write Model)   │    │ (Event Store)   │    │  (Read Model)   │
└─────────────────┘    └─────────┬───────┘    └─────────┬───────┘
                                 │                       ▲
                                 ▼                       │
                    ┌─────────────────────────────────────┤
                    │        Pub/Sub System               │
                    │  (In-Memory → Kafka Ready)          │
                    └─────────────┬───────────────────────┘
                                  │
                    ┌─────────────▼─────────────┐
                    │     Event Consumers       │
                    │   (Parallel Workers)      │
                    └───────────────────────────┘
```

### Event Flow

1. **API Request** → Modify MySQL data
2. **Outbox Event** → Store event in same transaction
3. **Event Publisher** → Async processing from outbox
4. **Pub/Sub System** → Distribute events to consumers
5. **ES Consumer** → Update Elasticsearch index
6. **Analytics Consumer** → Update metrics and analytics

## 🚀 Features

### Core Functionality
- ✅ **Product Management** - CRUD operations with full validation
- ✅ **Advanced Search** - Elasticsearch-powered with faceting and fuzzy search
- ✅ **Related Products** - ML-style "More Like This" recommendations
- ✅ **Analytics Tracking** - View and purchase event tracking
- ✅ **Real-time Sync** - Eventual consistency between data stores

### Architecture Patterns
- ✅ **Transactional Outbox** - Reliable event publishing
- ✅ **Event Sourcing** - Complete audit trail of changes
- ✅ **CQRS** - Optimized read/write models
- ✅ **Saga Pattern** - Distributed transaction management
- ✅ **Circuit Breaker** - Resilience against service failures

### Production Features
- ✅ **Comprehensive Testing** - Unit, integration, and contract tests
- ✅ **Observability** - Metrics, logging, and health checks
- ✅ **API Documentation** - OpenAPI/Swagger with examples
- ✅ **Docker Support** - Complete containerization
- ✅ **Configuration Management** - Profile-based configuration
- ✅ **Error Handling** - Structured error responses

## 🛠️ Technology Stack

### Backend
- **Java 17** - Modern LTS Java version
- **Spring Boot 3.5.6** - Latest Spring Boot with native compilation support
- **Spring Data JPA** - Database abstraction layer
- **Spring Data Elasticsearch** - Search engine integration
- **MapStruct** - Type-safe bean mapping
- **HikariCP** - High-performance connection pooling

### Data Storage
- **MySQL 8.0** - Primary data store (write model)
- **Elasticsearch 8.11** - Search engine (read model)
- **Redis** - Caching and session management (optional)

### Messaging & Events
- **Custom Pub/Sub** - In-memory message broker (Kafka-ready)
- **Transactional Outbox** - Reliable event publishing
- **Batch Processing** - Efficient message handling

### DevOps & Monitoring
- **Docker & Docker Compose** - Containerization
- **Prometheus** - Metrics collection
- **Grafana** - Metrics visualization
- **Actuator** - Health checks and application metrics

## 🚦 Quick Start

### Prerequisites
- Java 17 or higher
- Docker and Docker Compose
- Git

### 1. Clone the Repository
```bash
git clone <repository-url>
cd EcomBackend
```

### 2. Start with Docker (Recommended)
```bash
# Windows
scripts\docker-dev.bat

# Linux/Mac
chmod +x scripts/docker-dev.sh
./scripts/docker-dev.sh
```

This will start all services:
- **Application**: http://localhost:8080
- **API Documentation**: http://localhost:8080/swagger-ui.html
- **Elasticsearch**: http://localhost:9200
- **Kibana**: http://localhost:5601
- **Database Admin**: http://localhost:8081
- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3000 (admin/admin)

### 3. Manual Setup (Alternative)

#### Start Infrastructure
```bash
docker-compose up -d mysql elasticsearch redis
```

#### Configure Application
```bash
# Copy configuration
cp src/main/resources/application-dev.properties src/main/resources/application.properties

# Update database connection if needed
# spring.datasource.url=jdbc:mysql://localhost:3306/ecommerce
```

#### Run Application
```bash
# Using Maven
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Or using IDE
# Run BackendApplication.java with active profile: dev
```

## 📊 API Documentation

### Core Endpoints

#### Product Management
```http
POST   /api/v1/products              # Create product
GET    /api/v1/products/{id}         # Get product by ID
PUT    /api/v1/products/{id}         # Update product
DELETE /api/v1/products/{id}         # Delete product
```

#### Search & Discovery
```http
GET    /api/v1/products/search       # Advanced search with filters
GET    /api/v1/products/{id}/related # Get related products
GET    /api/v1/products/popular      # Get popular products
GET    /api/v1/products/autocomplete # Autocomplete suggestions
```

#### Admin Operations
```http
POST   /api/v1/admin/publish-batch   # Publish test events
POST   /api/v1/admin/sync/full       # Trigger full ES sync
GET    /api/v1/admin/sync/status     # Get sync status
```

#### Health & Monitoring
```http
GET    /api/v1/health                # Basic health check
GET    /api/v1/health/detailed       # Detailed health info
GET    /api/v1/health/metrics        # Application metrics
```

### Example Request
```bash
# Create a product
curl -X POST http://localhost:8080/api/v1/products \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Wireless Bluetooth Headphones",
    "description": "High-quality wireless headphones with noise cancellation",
    "categories": ["Electronics", "Audio"],
    "price": 99.99,
    "sku": "WBH-001",
    "attributes": {
      "color": "black",
      "battery_life": "30 hours",
      "noise_cancellation": "active"
    },
    "images": [
      "https://example.com/headphones-1.jpg",
      "https://example.com/headphones-2.jpg"
    ]
  }'
```

### Example Response
```json
{
  "success": true,
  "message": "Product created successfully",
  "data": {
    "id": 1,
    "name": "Wireless Bluetooth Headphones",
    "description": "High-quality wireless headphones with noise cancellation",
    "categories": ["Electronics", "Audio"],
    "price": 99.99,
    "sku": "WBH-001",
    "attributes": {
      "color": "black",
      "battery_life": "30 hours",
      "noise_cancellation": "active"
    },
    "images": [
      "https://example.com/headphones-1.jpg",
      "https://example.com/headphones-2.jpg"
    ],
    "createdAt": "2024-01-15T10:30:00",
    "updatedAt": "2024-01-15T10:30:00",
    "clickCount": 0,
    "purchaseCount": 0,
    "popularityScore": 0.0
  },
  "timestamp": "2024-01-15T10:30:00",
  "path": "/api/v1/products"
}
```

## 🏛️ Architecture Deep Dive

### Domain-Driven Design

The application follows DDD principles with clear bounded contexts:

```
src/main/java/com/Ecom/backend/
├── domain/                    # Domain Layer
│   └── entity/               # Domain entities with business logic
├── application/              # Application Layer
│   ├── dto/                 # Data Transfer Objects
│   ├── mapper/              # Entity-DTO mappers
│   └── service/             # Application services
├── infrastructure/          # Infrastructure Layer
│   ├── repository/          # Data access
│   ├── elasticsearch/       # Search integration
│   └── pubsub/             # Messaging
└── presentation/           # Presentation Layer
    ├── controller/         # REST controllers
    ├── dto/               # API DTOs
    └── exception/         # Error handling
```

### Event-Driven Architecture

#### Outbox Pattern Implementation
The transactional outbox pattern ensures reliable event publishing:

1. **Business Transaction** - Update product data in MySQL
2. **Event Storage** - Store event in outbox table (same transaction)
3. **Event Publishing** - Async processor publishes events
4. **Event Consumption** - Parallel consumers update Elasticsearch

```java
@Transactional
public ProductDto createProduct(CreateProductRequest request) {
    // 1. Save product to database
    Product product = productRepository.save(mappedProduct);
    
    // 2. Store event in outbox (same transaction)
    outboxEventService.storeEvent(
        product.getId().toString(),
        "Product",
        "ProductCreated",
        eventData
    );
    
    return productMapper.toDto(product);
}
```

#### Event Flow
```
MySQL Transaction → Outbox Event → Pub/Sub → ES Consumer → Search Index
                                        ↓
                                  Analytics Consumer → Metrics
```

### CQRS Implementation

- **Command Side (Write)**: MySQL optimized for transactions
- **Query Side (Read)**: Elasticsearch optimized for search
- **Synchronization**: Event-driven eventual consistency

### Pub/Sub System

Custom in-memory pub/sub that mimics Kafka:
- **Batch Publishing** - Efficient message publishing
- **Parallel Consumers** - Configurable worker threads
- **Offset Management** - Message ordering and acknowledgment
- **Retry Logic** - Exponential backoff and dead letter queues
- **Kafka Ready** - Easy migration to Apache Kafka

## 🧪 Testing Strategy

### Test Pyramid
- **Unit Tests** (70%) - Fast, focused tests for business logic
- **Integration Tests** (20%) - Test component interactions
- **End-to-End Tests** (10%) - Full system testing

### Test Categories
```bash
# Run all tests
./mvnw test

# Run only unit tests
./mvnw test -Dtest="**/*Test"

# Run only integration tests
./mvnw test -Dtest="**/*IntegrationTest"
```

### Key Test Scenarios
- ✅ **Outbox Pattern** - Event publishing reliability
- ✅ **Idempotency** - Duplicate message handling
- ✅ **Retry Logic** - Failure recovery
- ✅ **Concurrent Access** - Race condition handling
- ✅ **Event Ordering** - Message sequence preservation
- ✅ **Database Transactions** - ACID compliance

## 📈 Monitoring & Observability

### Health Checks
- **Application Health** - Service availability
- **Database Health** - Connection status
- **Elasticsearch Health** - Search service status
- **Pub/Sub Health** - Message system status

### Metrics
- **Business Metrics** - Product views, purchases, search queries
- **Technical Metrics** - Response times, error rates, throughput
- **Infrastructure Metrics** - CPU, memory, database connections

### Logging
- **Structured Logging** - JSON format with correlation IDs
- **Log Levels** - Environment-specific logging configuration
- **Audit Trail** - Complete event history

## 🔧 Configuration

### Environment Profiles
- **development** - Local development with debug logging
- **test** - In-memory databases for testing
- **docker** - Containerized environment
- **production** - Optimized for production deployment

### Key Configuration Options
```properties
# Pub/Sub Configuration
pubsub.consumer.batch-size=10
pubsub.consumer.poll-interval-ms=100
pubsub.consumer.default-worker-count=3

# Outbox Configuration
outbox.batch-size=50
outbox.processing-interval-ms=5000

# Elasticsearch Sync
elasticsearch.sync.batch-size=100
elasticsearch.sync.consumer-workers=2
```

## 🚀 Production Deployment

### Recommended Architecture
```
Load Balancer → [App Instance 1, App Instance 2, App Instance 3]
                      ↓
             [MySQL Cluster] ← → [Redis Cluster]
                      ↓
             [Kafka Cluster] → [Elasticsearch Cluster]
```

### Migration to Kafka
Replace the in-memory pub/sub with Apache Kafka:

1. **Add Kafka Dependencies**
```xml
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
```

2. **Implement Kafka Adapter**
```java
@Component
public class KafkaMessagePublisher implements MessagePublisher {
    // Implement using KafkaTemplate
}
```

3. **Update Configuration**
```properties
spring.kafka.bootstrap-servers=kafka:9092
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer
```

### Scaling Considerations
- **Database Sharding** - Partition data across multiple databases
- **Read Replicas** - Scale read operations
- **Caching Layer** - Add Redis for frequently accessed data
- **CDN Integration** - Serve static content from CDN
- **Auto-scaling** - Configure container orchestration

## 🤝 Contributing

### Development Guidelines
1. Follow **SOLID principles** and **Clean Architecture**
2. Write **comprehensive tests** for all new features
3. Maintain **backward compatibility** in API changes
4. Use **conventional commits** for version control
5. Update **documentation** for architectural changes

### Code Quality
- **SonarQube** integration for code quality metrics
- **Checkstyle** for coding standards
- **SpotBugs** for bug detection
- **JaCoCo** for test coverage

## 📚 Learning Resources

### Architecture Patterns
- [Microservices Patterns](https://microservices.io/) - Chris Richardson
- [Building Event-Driven Microservices](https://www.oreilly.com/library/view/building-event-driven-microservices/9781492057888/)
- [Domain-Driven Design](https://domainlanguage.com/ddd/) - Eric Evans

### Spring Boot & Java
- [Spring Boot Reference](https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/)
- [Spring Data JPA](https://spring.io/projects/spring-data-jpa)
- [Spring Data Elasticsearch](https://spring.io/projects/spring-data-elasticsearch)

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🆘 Support

For questions and support:
- **Documentation**: Check this README and code comments
- **Issues**: Use GitHub Issues for bug reports
- **Discussions**: Use GitHub Discussions for questions
- **Email**: Contact the development team at dev@example.com

---

**Built with ❤️ for learning and demonstrating production-ready architecture patterns.**
