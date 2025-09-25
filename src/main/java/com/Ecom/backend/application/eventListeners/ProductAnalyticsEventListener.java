package com.Ecom.backend.application.eventListeners;

import com.Ecom.backend.application.eventListeners.Interface.BaseEventListener;
import com.Ecom.backend.infrastructure.pubsub.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Event listener for analytics events (views, purchases, etc.)
 */
@Component
@Slf4j
public class ProductAnalyticsEventListener implements BaseEventListener {


    @Override
    public void createConsumers() {

    }

    @Override
    public void handleMessage(Message message) {

    }

    @Override
    public List<String> getSubscribedTopics() {
        return List.of();
    }

    @Override
    public void shutdown() {

    }
}
