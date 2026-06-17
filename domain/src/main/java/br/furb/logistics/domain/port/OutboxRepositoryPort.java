package br.furb.logistics.domain.port;

import br.furb.logistics.domain.event.DomainEvent;

import java.time.Instant;
import java.util.List;

public interface OutboxRepositoryPort {

    void save(DomainEvent event);

    List<OutboxEntry> claimPending(int batchSize, Instant claimedAt, Instant retryTimedOutBefore);

    void markAsPublished(String eventId, Instant publishedAt);

    RetryOutcome markForRetry(String eventId, String errorMessage, Instant nextAttemptAt, int maxAttempts);

    long deletePublishedBefore(Instant threshold);

    long countFailed();

    boolean existsEarlierUnpublished(String groupId, Instant createdAt, String outboxId);

    void releaseClaim(String eventId, Instant nextAttemptAt);

    record OutboxEntry(
            String id,
            String eventId,
            String eventType,
            String payload,
            String groupId,
            String traceparent,
            String tracestate,
            Instant createdAt,
            int retryCount
    ) {}

    record RetryOutcome(boolean retryScheduled, int retryCount) {}
}
