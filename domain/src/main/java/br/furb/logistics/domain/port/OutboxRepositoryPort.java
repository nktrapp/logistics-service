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

    /** Counts events that exhausted all publish retries (status FAILED) and require manual replay. */
    long countFailed();

    /**
     * True if another event of the same group precedes (createdAt, _id) and is not yet
     * PUBLISHED (PENDING, IN_PROGRESS or FAILED). A FAILED head deliberately blocks the
     * whole group until manual replay: per-group ordering is chosen over group liveness.
     */
    boolean existsEarlierUnpublished(String groupId, Instant createdAt, String outboxId);

    /** Returns a claimed entry to PENDING without counting a retry (ordering deferral, not a failure). */
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
