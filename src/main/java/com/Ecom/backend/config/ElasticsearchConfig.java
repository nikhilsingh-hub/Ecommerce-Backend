package com.Ecom.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

import java.time.Duration;

/**
 * Elasticsearch configuration for search functionality.
 * Configures connection settings and repository scanning.
 */
@Configuration
@EnableElasticsearchRepositories(basePackages = "com.Ecom.backend.infrastructure.elasticsearch")
public class ElasticsearchConfig extends ElasticsearchConfiguration {
    
    @Value("${spring.elasticsearch.rest.uris:http://localhost:9200}")
    private String elasticsearchUrl;
    
    @Value("${spring.elasticsearch.rest.username:}")
    private String username;
    
    @Value("${spring.elasticsearch.rest.password:}")
    private String password;
    
    @Value("${spring.elasticsearch.rest.connection-timeout:5s}")
    private Duration connectionTimeout;
    
    @Value("${spring.elasticsearch.rest.read-timeout:30s}")
    private Duration readTimeout;
    
    @Override
    public ClientConfiguration clientConfiguration() {
        String host = elasticsearchUrl.replace("http://", "").replace("https://", "");
        
        return ClientConfiguration.builder()
            .connectedTo(host)
            .build();
    }
}
