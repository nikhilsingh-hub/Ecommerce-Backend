package com.Ecom.backend.application.event;

import com.Ecom.backend.infrastructure.pubsub.Message;
import com.Ecom.backend.infrastructure.pubsub.MessageConsumerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Event listener for sending notifications (email, SMS, push notifications)
 */
@Component
@Slf4j
public class NotificationEventListener extends BaseEventListener {
    
    @Value("${notification.consumer-workers:2}")
    private int notificationWorkers;
    
    public NotificationEventListener(MessageConsumerFactory consumerFactory) {
        super(consumerFactory);
    }
    
    @Override
    protected String getConsumerGroupId() {
        return "notification-sync";
    }
    
    @Override
    protected List<String> getSubscribedTopics() {
        return List.of("order-events", "user-events", "product-events");
    }
    
    @Override
    protected int getWorkerCount() {
        return notificationWorkers;
    }
    
    @Override
    protected void handleMessage(Message message) {
        log.debug("Processing notification event: {}", message.getEventType());
        
        switch (message.getEventType()) {
            case "OrderPlaced" -> sendOrderConfirmation(message);
            case "OrderShipped" -> sendShippingNotification(message);
            case "ProductBackInStock" -> sendBackInStockNotification(message);
            case "UserRegistered" -> sendWelcomeEmail(message);
            default -> log.debug("No notification needed for event: {}", message.getEventType());
        }
    }
    
    private void sendOrderConfirmation(Message message) {
        log.info("Sending order confirmation notification");
        // Implement email/SMS sending logic
    }
    
    private void sendShippingNotification(Message message) {
        log.info("Sending shipping notification");
        // Implement shipping notification logic
    }
    
    private void sendBackInStockNotification(Message message) {
        log.info("Sending back-in-stock notification");
        // Implement back-in-stock notification logic
    }
    
    private void sendWelcomeEmail(Message message) {
        log.info("Sending welcome email");
        // Implement welcome email logic
    }
}
