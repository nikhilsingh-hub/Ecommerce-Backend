package com.Ecom.backend.application.event;

import com.Ecom.backend.infrastructure.pubsub.Message;
import com.Ecom.backend.infrastructure.pubsub.MessageConsumerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Event listener for analytics events (views, purchases, etc.)
 */
@Component
@Slf4j
public class ProductAnalyticsEventListener extends BaseEventListener {
    
    @Value("${analytics.consumer-workers:1}")
    private int analyticsWorkers;
    
    public ProductAnalyticsEventListener(MessageConsumerFactory consumerFactory) {
        super(consumerFactory);
    }
    
    @Override
    protected String getConsumerGroupId() {
        return "analytics-sync";
    }
    
    @Override
    protected List<String> getSubscribedTopics() {
        return List.of("product-events", "user-events");
    }
    
    @Override
    protected int getWorkerCount() {
        return analyticsWorkers;
    }
    
    @Override
    protected void handleMessage(Message message) {
        log.debug("Processing analytics event: {}", message.getEventType());
        
        switch (message.getEventType()) {
            case "ProductViewed" -> handleProductViewed(message);
            case "ProductPurchased" -> handleProductPurchased(message);
            case "UserRegistered" -> handleUserRegistered(message);
            default -> log.debug("Ignoring event type: {}", message.getEventType());
        }
    }
    
    private void handleProductViewed(Message message) {
        // Implement product view analytics
        log.debug("Processing product view event");
    }
    
    private void handleProductPurchased(Message message) {
        // Implement purchase analytics
        log.debug("Processing product purchase event");
    }
    
    private void handleUserRegistered(Message message) {
        // Implement user registration analytics
        log.debug("Processing user registration event");
    }
}
