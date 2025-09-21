package com.Ecom.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI (Swagger) configuration for API documentation.
 * Configures comprehensive API documentation with proper metadata.
 */
@Configuration
public class OpenApiConfig {
    
    @Value("${app.version:1.0.0}")
    private String appVersion;
    
    @Value("${app.name:E-commerce Backend API}")
    private String appName;
    
    @Value("${app.description:Production-minded E-commerce API with event-driven architecture}")
    private String appDescription;
    
    @Value("${server.port:8080}")
    private String serverPort;
    
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title(appName)
                .version(appVersion)
                .description(appDescription + "\n\n" +
                    "## Features\n" +
                    "- **Event-Driven Architecture**: Uses outbox pattern for reliable event publishing\n" +
                    "- **Advanced Search**: Elasticsearch integration with fuzzy search and faceting\n" +
                    "- **Pub/Sub System**: In-memory message broker with batch processing\n" +
                    "- **CQRS Pattern**: Separate read/write models with eventual consistency\n" +
                    "- **Production Ready**: Comprehensive testing, monitoring, and observability\n" +
                    "- **Scalable Design**: Easily replaceable components (Kafka, Redis, etc.)\n\n" +
                    "## Architecture\n" +
                    "The application follows Domain-Driven Design principles with clean architecture:\n" +
                    "- **Domain Layer**: Business entities and logic\n" +
                    "- **Application Layer**: Use cases and services\n" +
                    "- **Infrastructure Layer**: Data access and external integrations\n" +
                    "- **Presentation Layer**: REST controllers and DTOs\n\n" +
                    "## Event Flow\n" +
                    "1. API operation modifies MySQL data\n" +
                    "2. Event stored in outbox table (same transaction)\n" +
                    "3. Outbox processor publishes event to pub/sub\n" +
                    "4. Elasticsearch consumer updates search index\n" +
                    "5. Analytics consumer updates metrics")
                .contact(new Contact()
                    .name("Development Team")
                    .email("dev@example.com")
                    .url("https://github.com/example/ecommerce-backend"))
                .license(new License()
                    .name("MIT License")
                    .url("https://opensource.org/licenses/MIT")))
            .servers(List.of(
                new Server()
                    .url("http://localhost:" + serverPort)
                    .description("Development server"),
                new Server()
                    .url("https://api.example.com")
                    .description("Production server")
            ));
    }
}
