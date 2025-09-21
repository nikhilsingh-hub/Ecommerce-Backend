package com.Ecom.backend.infrastructure.repository;

import com.Ecom.backend.domain.entity.OutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository interface for OutboxEvent entity.
 * Supports the Transactional Outbox Pattern for reliable event publishing.
 */
@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Find unprocessed events ordered by creation time
     */
    @Query("SELECT e FROM OutboxEvent e WHERE e.processed = false ORDER BY e.createdAt ASC")
    List<OutboxEvent> findUnprocessedEvents(Pageable pageable);

    /**
     * Find events eligible for retry
     */
    @Query("SELECT e FROM OutboxEvent e WHERE e.processed = false AND " +
           "e.retryCount < 5 AND " +
           "(e.nextRetryAt IS NULL OR e.nextRetryAt <= :now) " +
           "ORDER BY e.createdAt ASC")
    List<OutboxEvent> findEventsForRetry(@Param("now") LocalDateTime now, Pageable pageable);

    /**
     * Find events that should be moved to dead letter queue
     */
    @Query("SELECT e FROM OutboxEvent e WHERE e.processed = false AND e.retryCount >= 5")
    List<OutboxEvent> findDeadLetterEvents(Pageable pageable);

    /**
     * Find events by aggregate ID and type
     */
    List<OutboxEvent> findByAggregateIdAndAggregateTypeOrderByCreatedAtAsc(String aggregateId, String aggregateType);

    /**
     * Find events by event type
     */
    List<OutboxEvent> findByEventTypeOrderByCreatedAtAsc(String eventType);

    /**
     * Mark event as processed
     */
    @Modifying
    @Query("UPDATE OutboxEvent e SET e.processed = true, e.processedAt = :processedAt WHERE e.id = :id")
    int markAsProcessed(@Param("id") Long id, @Param("processedAt") LocalDateTime processedAt);

    /**
     * Increment retry count and set next retry time
     */
    @Modifying
    @Query("UPDATE OutboxEvent e SET " +
           "e.retryCount = e.retryCount + 1, " +
           "e.nextRetryAt = :nextRetryAt, " +
           "e.errorMessage = :errorMessage " +
           "WHERE e.id = :id")
    int incrementRetryCount(
        @Param("id") Long id,
        @Param("nextRetryAt") LocalDateTime nextRetryAt,
        @Param("errorMessage") String errorMessage
    );

    /**
     * Delete old processed events (for cleanup)
     */
    @Modifying
    @Query("DELETE FROM OutboxEvent e WHERE e.processed = true AND e.processedAt < :cutoffDate")
    int deleteOldProcessedEvents(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Count unprocessed events
     */
    @Query("SELECT COUNT(e) FROM OutboxEvent e WHERE e.processed = false")
    long countUnprocessedEvents();

    /**
     * Count events by status for monitoring
     */
    @Query("SELECT e.processed, e.retryCount, COUNT(e) FROM OutboxEvent e " +
           "GROUP BY e.processed, e.retryCount")
    List<Object[]> getEventStatusStatistics();

    /**
     * Find events by aggregate ID for debugging/auditing
     */
    List<OutboxEvent> findByAggregateIdOrderByCreatedAtDesc(String aggregateId);

    /**
     * Find recent events for monitoring
     */
    @Query("SELECT e FROM OutboxEvent e WHERE e.createdAt >= :since ORDER BY e.createdAt DESC")
    List<OutboxEvent> findRecentEvents(@Param("since") LocalDateTime since, Pageable pageable);
}
