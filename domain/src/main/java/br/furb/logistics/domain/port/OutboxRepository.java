package br.furb.logistics.domain.port;

import br.furb.logistics.domain.event.DomainEvent;

import java.time.Instant;
import java.util.List;

public interface OutboxRepository {

    void save(DomainEvent event);

    List<OutboxEntry> claimPending(int batchSize, Instant claimedAt, Instant retryTimedOutBefore);

    void markAsPublished(String eventId, Instant publishedAt);

    RetryOutcome markForRetry(String eventId, String errorMessage, Instant nextAttemptAt, int maxAttempts);

    /** Removes already-published events older than the threshold. Returns the number of deleted entries. */
    long deletePublishedBefore(Instant threshold);

    /** Counts events that exhausted all publish retries (status FAILED) and require manual replay. */
    long countFailed();

    record OutboxEntry(
            String eventId,
            String eventType,
            String payload,
            String groupId,
            Instant createdAt,
            int retryCount
    ) {}

    record RetryOutcome(boolean retryScheduled, int retryCount) {}
}
