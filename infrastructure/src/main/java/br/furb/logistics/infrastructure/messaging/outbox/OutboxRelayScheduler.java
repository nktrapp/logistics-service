package br.furb.logistics.infrastructure.messaging.outbox;

import br.furb.logistics.core.port.EventPublisherPort;
import br.furb.logistics.domain.port.OutboxRepository;
import br.furb.logistics.domain.port.OutboxRepository.OutboxEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayScheduler {

    private final OutboxRepository outboxRepository;
    private final EventPublisherPort eventPublisherPort;
    private final ObjectMapper objectMapper;

    @Value("${app.messaging.outbound-queue:logistics-events-queue}")
    private String outboundQueue;

    @Value("${app.outbox.relay.batch-size:50}")
    private int batchSize;

    @Value("${app.outbox.relay.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.outbox.relay.retry-delay-ms:5000}")
    private long retryDelayMs;

    @Value("${app.outbox.relay.processing-timeout-ms:60000}")
    private long processingTimeoutMs;

    @Scheduled(fixedDelayString = "${app.outbox.relay.fixed-delay-ms:2000}")
    public void relay() {
        Instant claimedAt = Instant.now();
        Instant retryTimedOutBefore = claimedAt.minusMillis(processingTimeoutMs);
        List<OutboxEntry> pendingEvents = outboxRepository.claimPending(batchSize, claimedAt, retryTimedOutBefore);

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.info("[outbox-relay] Found {} pending events to publish", pendingEvents.size());

        for (OutboxEntry entry : pendingEvents) {
            try {
                String envelope = buildEnvelope(entry);
                eventPublisherPort.publish(outboundQueue, envelope);
                outboxRepository.markAsPublished(entry.eventId(), Instant.now());
            } catch (Exception e) {
                OutboxRepository.RetryOutcome retryOutcome = outboxRepository.markForRetry(
                        entry.eventId(),
                        e.getMessage(),
                        Instant.now().plusMillis(retryDelayMs),
                        maxAttempts
                );

                if (retryOutcome.retryScheduled()) {
                    log.error("[outbox-relay] Failed to publish event {}, scheduling retry {}", entry.eventId(), retryOutcome.retryCount(), e);
                    continue;
                }

                log.error("[outbox-relay] Failed to publish event {} after {} attempts, marking as failed", entry.eventId(), retryOutcome.retryCount(), e);
            }
        }
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
