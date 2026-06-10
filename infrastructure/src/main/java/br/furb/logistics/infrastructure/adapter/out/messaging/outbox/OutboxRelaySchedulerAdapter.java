package br.furb.logistics.infrastructure.adapter.out.messaging.outbox;

import br.furb.logistics.domain.port.EventPublisherPort;
import br.furb.logistics.domain.port.OutboxRepositoryPort;
import br.furb.logistics.domain.port.OutboxRepositoryPort.OutboxEntry;
import br.furb.logistics.domain.port.OutboxRepositoryPort.RetryOutcome;
import br.furb.logistics.infrastructure.config.TraceContextSupport;
import io.micrometer.tracing.Span;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelaySchedulerAdapter {

    private static final int MAX_BACKOFF_SHIFT = 30;

    private final OutboxRepositoryPort outboxRepository;
    private final EventPublisherPort eventPublisherPort;
    private final ObjectMapper objectMapper;
    private final TraceContextSupport traceContextSupport;

    @Value("${app.messaging.outbound-queue:logistics-events-queue.fifo}")
    private String outboundQueue;

    @Value("${app.messaging.hub-events-queue:hub-events-queue.fifo}")
    private String hubEventsQueue;

    @Value("${app.outbox.relay.batch-size:50}")
    private int batchSize;

    @Value("${app.outbox.relay.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.outbox.relay.retry-delay-ms:5000}")
    private long retryDelayMs;

    @Value("${app.outbox.relay.max-retry-delay-ms:60000}")
    private long maxRetryDelayMs;

    @Value("${app.outbox.relay.processing-timeout-ms:60000}")
    private long processingTimeoutMs;

    @Value("${app.outbox.retention.published-retention-ms:604800000}")
    private long publishedRetentionMs;

    @Scheduled(fixedDelayString = "${app.outbox.relay.fixed-delay-ms:2000}")
    public void relay() {
        List<OutboxEntry> pendingEvents;
        try {
            Instant claimedAt = Instant.now();
            Instant retryTimedOutBefore = claimedAt.minusMillis(processingTimeoutMs);
            pendingEvents = outboxRepository.claimPending(batchSize, claimedAt, retryTimedOutBefore);
        } catch (Exception e) {
            recordScheduledFailure("outbox.relay.tick", "outbox.relay", "[outbox-relay] Scheduled relay tick failed", e);
            throw e;
        }

        if (pendingEvents.isEmpty()) {
            return;
        }

        try (TraceContextSupport.ScopedSpan span = traceContextSupport.startSpan("outbox.relay.tick")) {
            span.tag("job.name", "outbox.relay");
            span.tag("messaging.system", "sqs");
            span.tag("outbox.claimed.count", Integer.toString(pendingEvents.size()));

            try {
                log.info("[outbox-relay] Found {} pending events to publish", pendingEvents.size());

                for (OutboxEntry entry : pendingEvents) {
                    publishEntry(entry);
                }
            } catch (Exception e) {
                span.error(e);
                log.error("[outbox-relay] Scheduled relay tick failed", e);
                throw e;
            }
        }
    }

    private void publishEntry(OutboxEntry entry) {
        String targetQueue = resolveTargetQueue(entry.eventType());
        TraceContextSupport.TraceCarrier traceCarrier = new TraceContextSupport.TraceCarrier(entry.traceparent(), entry.tracestate());
        try (TraceContextSupport.ScopedSpan span = traceContextSupport.startSpan("outbox.relay.publish", traceCarrier, Span.Kind.PRODUCER)) {
            span.tag("messaging.system", "sqs");
            span.tag("messaging.operation", "publish");
            span.tag("messaging.destination.name", targetQueue);
            span.tag("messaging.message.id", entry.eventId());
            span.tag("event.id", entry.eventId());
            span.tag("event.type", entry.eventType());

            MDC.put("eventId", entry.eventId());
            MDC.put("eventType", entry.eventType());
            try {
                String groupId = entry.groupId() != null ? entry.groupId() : entry.eventId();
                if (outboxRepository.existsEarlierUnpublished(groupId, entry.createdAt(), entry.id())) {
                    outboxRepository.releaseClaim(entry.eventId(), Instant.now().plusMillis(retryDelayMs));
                    log.debug("[outbox-relay] Event {} deferred: earlier event of group {} not yet published", entry.eventId(), groupId);
                    return;
                }
                String envelope = buildEnvelope(entry);
                eventPublisherPort.publish(targetQueue, envelope, groupId, entry.eventId());
                outboxRepository.markAsPublished(entry.eventId(), Instant.now());
                log.debug("[outbox-relay] Event {} published successfully", entry.eventId());
            } catch (Exception e) {
                span.error(e);
                scheduleRetry(entry, e);
            } finally {
                MDC.remove("eventId");
                MDC.remove("eventType");
            }
        }
    }

    private void scheduleRetry(OutboxEntry entry, Exception e) {
        long backoffMillis = computeBackoffMillis(entry.retryCount());
        RetryOutcome retryOutcome = outboxRepository.markForRetry(
                entry.eventId(),
                e.getMessage(),
                Instant.now().plusMillis(backoffMillis),
                maxAttempts
        );

        if (retryOutcome.retryScheduled()) {
            log.error("[outbox-relay] Failed to publish event {}, scheduling retry {}", entry.eventId(), retryOutcome.retryCount(), e);
            return;
        }

        log.error("[outbox-relay] Failed to publish event {} after {} attempts, marking as failed", entry.eventId(), retryOutcome.retryCount(), e);
    }

    @Scheduled(fixedDelayString = "${app.outbox.retention.fixed-delay-ms:3600000}")
    public void purgePublished() {
        Instant threshold = Instant.now().minusMillis(publishedRetentionMs);
        long deleted;
        try {
            deleted = outboxRepository.deletePublishedBefore(threshold);
        } catch (Exception e) {
            recordScheduledFailure("outbox.retention.tick", "outbox.retention", "[outbox-retention] Scheduled retention tick failed", e);
            throw e;
        }
        if (deleted == 0) {
            return;
        }

        try (TraceContextSupport.ScopedSpan span = traceContextSupport.startSpan("outbox.retention.tick")) {
            span.tag("job.name", "outbox.retention");
            span.tag("outbox.deleted.count", Long.toString(deleted));
            log.info("[outbox-retention] Purged {} published events older than {}", deleted, threshold);
        }
    }

    private void recordScheduledFailure(String spanName, String jobName, String message, Exception exception) {
        try (TraceContextSupport.ScopedSpan span = traceContextSupport.startSpan(spanName)) {
            span.tag("job.name", jobName);
            span.error(exception);
            log.error(message, exception);
        }
    }

    private long computeBackoffMillis(int retryCount) {
        if (retryCount > MAX_BACKOFF_SHIFT) {
            return maxRetryDelayMs;
        }
        long delay = retryDelayMs * (1L << retryCount);
        return Math.min(delay, maxRetryDelayMs);
    }

    private String resolveTargetQueue(String eventType) {
        if (eventType != null && eventType.startsWith("hub.")) {
            return hubEventsQueue;
        }
        return outboundQueue;
    }

    private String buildEnvelope(OutboxEntry entry) {
        try {
            ObjectNode envelope = objectMapper.createObjectNode();
            envelope.put("eventId", entry.eventId());
            envelope.put("eventType", entry.eventType());
            envelope.put("occurredAt", entry.createdAt().toString());
            envelope.put("source", "logistics-service");
            envelope.put("version", "1.0");
            envelope.set("payload", objectMapper.readTree(entry.payload()));
            return objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build event envelope for event: " + entry.eventId(), e);
        }
    }
}
