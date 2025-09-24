package com.Ecom.backend.infrastructure.pubsub;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * In-memory implementation of MessageConsumer with parallel processing capabilities.
 * Supports batch consumption, retry logic, and dead letter handling.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class InMemoryMessageConsumer implements MessageConsumer {
    
    private final InMemoryMessageBroker broker;
    
    @Value("${pubsub.consumer.batch-size:10}")
    private int batchSize;
    
    @Value("${pubsub.consumer.poll-interval-ms:100}")
    private long pollIntervalMs;
    
    @Value("${pubsub.consumer.max-retries:3}")
    private int maxRetries;
    
    @Value("${pubsub.consumer.retry-delay-ms:1000}")
    private long retryDelayMs;
    
    private String consumerGroup;
    private Consumer<Message> messageHandler;
    private Consumer<MessageBatch> batchHandler;
    private final AtomicBoolean isConsuming = new AtomicBoolean(false);
    private ExecutorService consumerExecutor;
    private ScheduledExecutorService retryExecutor;
    private CompletableFuture<Void> consumerTask;
    
    // Statistics tracking
    private final AtomicLong totalMessagesConsumed = new AtomicLong(0);
    private final AtomicLong totalBatchesConsumed = new AtomicLong(0);
    private final AtomicLong failedMessages = new AtomicLong(0);
    private final AtomicLong retriedMessages = new AtomicLong(0);
    private final AtomicLong deadLetterMessages = new AtomicLong(0);
    private final AtomicLong totalProcessingTime = new AtomicLong(0);
    private volatile long lastConsumeTimestamp = 0;
    private volatile long currentOffset = 0;
    
    @Override
    public void subscribe(String topic, String consumerGroup) {
        subscribe(List.of(topic), consumerGroup);
    }
    
    @Override
    public void subscribe(List<String> topics, String consumerGroup) {
        this.consumerGroup = consumerGroup;
        broker.subscribe(consumerGroup, topics);
        log.info("✅ [CONSUMER DEBUG] Subscribed consumer group {} to topics {}", consumerGroup, topics);
    }
    
    @Override
    public void setMessageHandler(Consumer<Message> handler) {
        this.messageHandler = handler;
        log.info("🎯 [CONSUMER DEBUG] Message handler set for consumer group {}, handler: {}", 
            consumerGroup, handler != null ? handler.getClass().getSimpleName() : "null");
    }
    
    @Override
    public void setBatchHandler(Consumer<MessageBatch> handler) {
        this.batchHandler = handler;
    }
    
    @Override
    public void startConsuming() {
        log.info("🚀 [CONSUMER DEBUG] Attempting to start consuming for group {}, isConsuming: {}", 
            consumerGroup, isConsuming.get());
        
        if (isConsuming.compareAndSet(false, true)) {
            log.info("🔧 [CONSUMER DEBUG] Creating executor services for group {}", consumerGroup);
            
            consumerExecutor = Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "consumer-" + consumerGroup);
                t.setDaemon(true);
                log.info("📍 [CONSUMER DEBUG] Created consumer thread: {}", t.getName());
                return t;
            });
            
            retryExecutor = Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "retry-" + consumerGroup);
                t.setDaemon(true);
                return t;
            });
            
            log.info("🔄 [CONSUMER DEBUG] Starting async consume loop for group {}", consumerGroup);
            consumerTask = CompletableFuture.runAsync(this::consumeLoop, consumerExecutor);
            
            log.info("✅ [CONSUMER DEBUG] Started consuming for consumer group {}, handler: {}", 
                consumerGroup, messageHandler != null ? "SET" : "NULL");
        } else {
            log.warn("⚠️ [CONSUMER DEBUG] Consumer group {} already started or failed to start", consumerGroup);
        }
    }
    
    @Override
    public void stopConsuming() {
        if (isConsuming.compareAndSet(true, false)) {
            if (consumerTask != null) {
                consumerTask.cancel(true);
            }
            
            shutdownExecutor(consumerExecutor, "consumer");
            shutdownExecutor(retryExecutor, "retry");
            
            log.info("Stopped consuming for consumer group {}", consumerGroup);
        }
    }
    
    private void shutdownExecutor(ExecutorService executor, String name) {
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }
        }
    }
    
    private void consumeLoop() {
        log.info("🔄 [CONSUMER DEBUG] Consumer loop STARTED for group {}, thread: {}", 
            consumerGroup, Thread.currentThread().getName());
        
        int pollCount = 0;
        while (isConsuming.get() && !Thread.currentThread().isInterrupted()) {
            try {
                pollCount++;
                log.debug("📡 [CONSUMER DEBUG] Polling attempt #{} for group {}", pollCount, consumerGroup);
                
                MessageBatch batch = broker.pollMessages(consumerGroup, batchSize);
                
                if (!batch.isEmpty()) {
                    log.info("📬 [CONSUMER DEBUG] Received {} messages for group {} on poll #{}", 
                        batch.getMessages().size(), consumerGroup, pollCount);
                    processBatch(batch);
                    lastConsumeTimestamp = System.currentTimeMillis();
                } else {
                    if (pollCount % 100 == 0) { // Log every 100 empty polls to avoid spam
                        log.debug("📭 [CONSUMER DEBUG] No messages on poll #{} for group {}, sleeping {}ms", 
                            pollCount, consumerGroup, pollIntervalMs);
                    }
                    Thread.sleep(pollIntervalMs);
                }
            } catch (InterruptedException e) {
                log.info("⏹️ [CONSUMER DEBUG] Consumer loop interrupted for group {}", consumerGroup);
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("❌ [CONSUMER DEBUG] Error in consumer loop for group {} on poll #{}", 
                    consumerGroup, pollCount, e);
                try {
                    Thread.sleep(pollIntervalMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        log.info("🛑 [CONSUMER DEBUG] Consumer loop ENDED for group {}, total polls: {}", 
            consumerGroup, pollCount);
    }
    
    private void processBatch(MessageBatch batch) {
        long startTime = System.currentTimeMillis();
        
        log.info("🎭 [CONSUMER DEBUG] Processing batch with {} messages for group {}", 
            batch.getMessages().size(), consumerGroup);
        
        try {
            if (batchHandler != null) {
                log.info("📦 [CONSUMER DEBUG] Using batch handler for group {}", consumerGroup);
                batchHandler.accept(batch);
                acknowledgeBatch(batch);
            } else if (messageHandler != null) {
                log.info("📄 [CONSUMER DEBUG] Processing {} messages individually for group {}", 
                    batch.getMessages().size(), consumerGroup);
                
                // Process messages individually in parallel
                List<CompletableFuture<Void>> futures = batch.getMessages().stream()
                    .map(message -> CompletableFuture.runAsync(() -> {
                        try {
                            log.debug("🔍 [CONSUMER DEBUG] Processing message {} of type {} for group {}", 
                                message.getId(), message.getEventType(), consumerGroup);
                            messageHandler.accept(message);
                            acknowledge(message);
                            log.debug("✅ [CONSUMER DEBUG] Successfully processed message {} for group {}", 
                                message.getId(), consumerGroup);
                        } catch (Exception e) {
                            log.error("❌ [CONSUMER DEBUG] Failed to process message {} for group {}", 
                                message.getId(), consumerGroup, e);
                            handleMessageFailure(message, e);
                        }
                    }, consumerExecutor))
                    .toList();
                
                // Wait for all messages in batch to complete
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                log.info("🏁 [CONSUMER DEBUG] Completed processing batch for group {}", consumerGroup);
            } else {
                log.error("⚠️ [CONSUMER DEBUG] No handler set for group {} - skipping batch", consumerGroup);
            }
            
            totalBatchesConsumed.incrementAndGet();
            long processingTime = System.currentTimeMillis() - startTime;
            totalProcessingTime.addAndGet(processingTime);
            
            log.debug("Processed batch {} with {} messages in {}ms", 
                batch.getBatchId(), batch.size(), processingTime);
                
        } catch (Exception e) {
            log.error("Failed to process batch {}", batch.getBatchId(), e);
            markBatchFailed(batch, e);
        }
    }
    
    private void handleMessageFailure(Message message, Exception e) {
        log.warn("Failed to process message {}: {}", message.getId(), e.getMessage());
        markFailed(message, e);
    }
    
    @Override
    public void acknowledge(Message message) {
        totalMessagesConsumed.incrementAndGet();
        currentOffset = Math.max(currentOffset, message.getOffset());
        broker.commitOffset(consumerGroup, message.getTopic(), message.getOffset());
        log.debug("Acknowledged message {} with offset {}", message.getId(), message.getOffset());
    }
    
    @Override
    public void acknowledgeBatch(MessageBatch batch) {
        totalMessagesConsumed.addAndGet(batch.size());
        
        if (!batch.isEmpty()) {
            currentOffset = Math.max(currentOffset, batch.getEndOffset());
            broker.commitOffset(consumerGroup, batch.getTopic(), batch.getEndOffset());
            log.debug("Acknowledged batch {} with {} messages, end offset {}", 
                batch.getBatchId(), batch.size(), batch.getEndOffset());
        }
    }
    
    @Override
    public void markFailed(Message message, Throwable error) {
        failedMessages.incrementAndGet();
        
        // Simple retry logic - in a real implementation, this would be more sophisticated
        if (shouldRetry(message)) {
            scheduleRetry(message, error);
        } else {
            sendToDeadLetter(message, error);
        }
    }
    
    @Override
    public void markBatchFailed(MessageBatch batch, Throwable error) {
        // For batch failures, mark individual messages as failed
        for (Message message : batch.getMessages()) {
            markFailed(message, error);
        }
    }
    
    private boolean shouldRetry(Message message) {
        // Simple retry logic based on message headers
        String retryCountHeader = message.getHeaders().get("retry-count");
        int retryCount = retryCountHeader != null ? Integer.parseInt(retryCountHeader) : 0;
        return retryCount < maxRetries;
    }
    
    private void scheduleRetry(Message message, Throwable error) {
        retriedMessages.incrementAndGet();
        
        retryExecutor.schedule(() -> {
            try {
                // Add retry count to headers
                String retryCountHeader = message.getHeaders().get("retry-count");
                int retryCount = retryCountHeader != null ? Integer.parseInt(retryCountHeader) : 0;
                message.getHeaders().put("retry-count", String.valueOf(retryCount + 1));
                
                if (messageHandler != null) {
                    messageHandler.accept(message);
                    acknowledge(message);
                }
            } catch (Exception e) {
                markFailed(message, e);
            }
        }, retryDelayMs * (1L << getRetryCount(message)), TimeUnit.MILLISECONDS);
        
        log.debug("Scheduled retry for message {} after {}ms", 
            message.getId(), retryDelayMs * (1L << getRetryCount(message)));
    }
    
    private void sendToDeadLetter(Message message, Throwable error) {
        deadLetterMessages.incrementAndGet();
        log.warn("Sending message {} to dead letter queue after {} retries. Error: {}", 
            message.getId(), maxRetries, error.getMessage());
        
        // In a real implementation, this would send to a dead letter topic
        // For now, we just log and acknowledge to prevent reprocessing
        acknowledge(message);
    }
    
    private int getRetryCount(Message message) {
        String retryCountHeader = message.getHeaders().get("retry-count");
        return retryCountHeader != null ? Integer.parseInt(retryCountHeader) : 0;
    }
    
    @Override
    public ConsumerStats getStats() {
        long totalMessages = totalMessagesConsumed.get();
        double averageProcessingTime = totalMessages > 0 ? 
            (double) totalProcessingTime.get() / totalMessages : 0.0;
        
        return new ConsumerStats(
            totalMessages,
            totalBatchesConsumed.get(),
            failedMessages.get(),
            retriedMessages.get(),
            deadLetterMessages.get(),
            averageProcessingTime,
            lastConsumeTimestamp,
            currentOffset
        );
    }
    
    @Override
    public void close() {
        stopConsuming();
        log.info("InMemoryMessageConsumer closed. Final stats: {}", getStats());
    }
}
