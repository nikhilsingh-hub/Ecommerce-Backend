package com.Ecom.backend.application.eventListeners;

import com.Ecom.backend.Enums.ConsumerGroupEnums;
import com.Ecom.backend.Enums.TopicEnum;
import com.Ecom.backend.application.eventListeners.Interface.BaseEventListener;
import com.Ecom.backend.application.service.ProductElasticsearchSyncService;
import com.Ecom.backend.infrastructure.pubsub.Message;
import com.Ecom.backend.infrastructure.pubsub.MessageConsumer;
import com.Ecom.backend.infrastructure.pubsub.MessageConsumerFactory;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Event listener for product-related events that syncs data to Elasticsearch
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ProductElasticsearchEventListener implements BaseEventListener {

    public final MessageConsumerFactory consumerFactory;
    private final ProductElasticsearchSyncService syncService;
    
    @Value("${elasticsearch.sync.consumer-workers:2}")
    private int elasticsearchWorkers;

    private List<MessageConsumer> consumers;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeConsumers() {
        log.info("Initializing Elasticsearch sync consumers");

        createConsumers();
        // Start consuming
        consumerFactory.startConsumerGroup(ConsumerGroupEnums.ELASTICSEARCH_SYNC.getValue());

        log.info("Elasticsearch sync consumers started with {} workers", elasticsearchWorkers);
    }

    public void handleMessage(Message message) {
        syncService.handleProductEvent(message);
    }

    public void shutdown() {

    }


    public void createConsumers() {
        consumers = consumerFactory.createConsumerGroup(
                ConsumerGroupEnums.ELASTICSEARCH_SYNC.getValue(),
                getSubscribedTopics(),
                elasticsearchWorkers,
                this::handleMessage
        );
    }

    @PreDestroy
    public void cleanup() {
        if (consumers != null) {
            consumers.forEach(MessageConsumer::close);
        }
        log.info("Elasticsearch sync service cleanup completed");
    }

    public List<String> getSubscribedTopics() {
        return List.of(TopicEnum.PRODUCT_EVENT.getValue());
    }
}
