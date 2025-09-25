package com.Ecom.backend.application.eventListeners.Interface;

import com.Ecom.backend.infrastructure.pubsub.DTO.Message;

import java.util.List;

/**
 * Base class for event listeners that provides common functionality
 * for creating and managing message consumers.
 */
public interface BaseEventListener {
    
    public void createConsumers();
    
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
