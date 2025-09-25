package com.Ecom.backend.application.event;

import com.Ecom.backend.infrastructure.pubsub.Message;
import com.Ecom.backend.infrastructure.pubsub.MessageConsumer;
import com.Ecom.backend.infrastructure.pubsub.MessageConsumerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.util.List;

/**
 * Base class for event listeners that provides common functionality
 * for creating and managing message consumers.
 */
public interface BaseEventListener {
    
    public List<MessageConsumer> createConsumers();
    
    /**
     * Handle incoming messages
     * Override this method to implement specific event handling logic
     */
    public abstract void handleMessage(Message message);

    
    /**
     * Get the topics this listener subscribes to
     */
    public abstract List<String> getSubscribedTopics();

    
    /**
     * Shutdown consumers when application stops
     */
    public void shutdown();
}
