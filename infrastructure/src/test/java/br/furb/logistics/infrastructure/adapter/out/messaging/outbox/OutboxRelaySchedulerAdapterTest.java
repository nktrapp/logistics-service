package br.furb.logistics.infrastructure.adapter.out.messaging.outbox;

import br.furb.logistics.domain.port.EventPublisherPort;
import br.furb.logistics.domain.port.OutboxRepositoryPort;
import br.furb.logistics.domain.port.OutboxRepositoryPort.OutboxEntry;
import br.furb.logistics.domain.port.OutboxRepositoryPort.RetryOutcome;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxRelaySchedulerAdapter")
class OutboxRelaySchedulerAdapterTest {

    @Mock
    OutboxRepositoryPort outboxRepository;

    @Mock
    EventPublisherPort eventPublisherPort;

    @Nested
    @DisplayName("Relay")
    class Relay {

        @Test
        @DisplayName("Given a pending event, should publish to the FIFO queue and mark it as published when relay succeeds")
        void shouldPublishAndMarkAsPublishedWhenRelaySucceeds() {
            OutboxRelaySchedulerAdapter scheduler = buildScheduler();
            OutboxEntry entry = new OutboxEntry(
                    "event-1",
                    "route.calculated",
                    "{\"packageId\":\"pkg-1\"}",
                    "pkg-1",
                    Instant.parse("2026-05-31T10:00:00Z"),
                    0
            );
            when(outboxRepository.claimPending(eq(10), argThat(Objects::nonNull), argThat(Objects::nonNull)))
                    .thenReturn(List.of(entry));

            scheduler.relay();

            ArgumentCaptor<String> envelopeCaptor = ArgumentCaptor.forClass(String.class);
            verify(eventPublisherPort).publish(eq("logistics-events-queue"), envelopeCaptor.capture(), eq("pkg-1"), eq("event-1"));
            verify(outboxRepository).markAsPublished(eq("event-1"), argThat(Objects::nonNull));
            verify(outboxRepository, never()).markForRetry(any(), any(), any(Instant.class), anyInt());

            assertThat(envelopeCaptor.getValue())
                    .contains("\"eventId\":\"event-1\"")
                    .contains("\"source\":\"logistics-service\"");
        }

        @Test
        @DisplayName("Given a publishing failure, should schedule retry when attempts are still available")
        void shouldScheduleRetryWhenPublishingFails() {
            OutboxRelaySchedulerAdapter scheduler = buildScheduler();
            OutboxEntry entry = new OutboxEntry(
                    "event-1",
                    "route.calculated",
                    "{\"packageId\":\"pkg-1\"}",
                    "pkg-1",
                    Instant.parse("2026-05-31T10:00:00Z"),
                    1
            );
            when(outboxRepository.claimPending(eq(10), argThat(Objects::nonNull), argThat(Objects::nonNull)))
                    .thenReturn(List.of(entry));
            when(outboxRepository.markForRetry(eq("event-1"), eq("boom"), argThat(Objects::nonNull), eq(5)))
                    .thenReturn(new RetryOutcome(true, 2));
            doThrow(new RuntimeException("boom"))
                    .when(eventPublisherPort)
                    .publish(eq("logistics-events-queue"), argThat(message -> message.contains("event-1")), eq("pkg-1"), eq("event-1"));

            scheduler.relay();

            verify(outboxRepository).markForRetry(eq("event-1"), eq("boom"), argThat(Objects::nonNull), eq(5));
            verify(outboxRepository, never()).markAsPublished(eq("event-1"), argThat(Objects::nonNull));
        }

        @Test
        @DisplayName("Given a publishing failure, should grow the retry delay exponentially with the entry retry count")
        void shouldUseExponentialBackoffForRetryDelay() {
            OutboxRelaySchedulerAdapter scheduler = buildScheduler();
            OutboxEntry entry = new OutboxEntry(
                    "event-1",
                    "route.calculated",
                    "{\"packageId\":\"pkg-1\"}",
                    "pkg-1",
                    Instant.parse("2026-05-31T10:00:00Z"),
                    3
            );
            when(outboxRepository.claimPending(eq(10), argThat(Objects::nonNull), argThat(Objects::nonNull)))
                    .thenReturn(List.of(entry));
            when(outboxRepository.markForRetry(eq("event-1"), eq("boom"), any(Instant.class), eq(5)))
                    .thenReturn(new RetryOutcome(true, 4));
            doThrow(new RuntimeException("boom"))
                    .when(eventPublisherPort)
                    .publish(eq("logistics-events-queue"), argThat(message -> message.contains("event-1")), eq("pkg-1"), eq("event-1"));

            Instant before = Instant.now();
            scheduler.relay();
            Instant after = Instant.now();

            ArgumentCaptor<Instant> nextAttemptCaptor = ArgumentCaptor.forClass(Instant.class);
            verify(outboxRepository).markForRetry(eq("event-1"), eq("boom"), nextAttemptCaptor.capture(), eq(5));
            // retryCount = 3 -> 5000ms * 2^3 = 40000ms
            assertThat(nextAttemptCaptor.getValue())
                    .isBetween(before.plusMillis(40000), after.plusMillis(40000));
        }

        @Test
        @DisplayName("Given a high retry count, should cap the exponential backoff delay at max-retry-delay-ms")
        void shouldCapExponentialBackoffDelay() {
            OutboxRelaySchedulerAdapter scheduler = buildScheduler();
            OutboxEntry entry = new OutboxEntry(
                    "event-1",
                    "route.calculated",
                    "{\"packageId\":\"pkg-1\"}",
                    "pkg-1",
                    Instant.parse("2026-05-31T10:00:00Z"),
                    10
            );
            when(outboxRepository.claimPending(eq(10), argThat(Objects::nonNull), argThat(Objects::nonNull)))
                    .thenReturn(List.of(entry));
            when(outboxRepository.markForRetry(eq("event-1"), eq("boom"), any(Instant.class), eq(5)))
                    .thenReturn(new RetryOutcome(true, 11));
            doThrow(new RuntimeException("boom"))
                    .when(eventPublisherPort)
                    .publish(eq("logistics-events-queue"), argThat(message -> message.contains("event-1")), eq("pkg-1"), eq("event-1"));

            Instant before = Instant.now();
            scheduler.relay();
            Instant after = Instant.now();

            ArgumentCaptor<Instant> nextAttemptCaptor = ArgumentCaptor.forClass(Instant.class);
            verify(outboxRepository).markForRetry(eq("event-1"), eq("boom"), nextAttemptCaptor.capture(), eq(5));
            // retryCount = 10 -> 5000ms * 2^10 = 5_120_000ms, capped to 60000ms
            assertThat(nextAttemptCaptor.getValue())
                    .isBetween(before.plusMillis(60000), after.plusMillis(60000));
        }
    }

    @Nested
    @DisplayName("Queue routing")
    class QueueRouting {

        @Test
        @DisplayName("Given a route.* event, should publish to the outbound (logistics) queue")
        void shouldRouteRouteEventsToOutboundQueue() {
            OutboxRelaySchedulerAdapter scheduler = buildScheduler();
            OutboxEntry entry = new OutboxEntry("event-1", "route.calculated", "{\"packageId\":\"pkg-1\"}",
                    "pkg-1", Instant.parse("2026-05-31T10:00:00Z"), 0);
            when(outboxRepository.claimPending(eq(10), argThat(Objects::nonNull), argThat(Objects::nonNull)))
                    .thenReturn(List.of(entry));

            scheduler.relay();

            verify(eventPublisherPort).publish(eq("logistics-events-queue"), any(), eq("pkg-1"), eq("event-1"));
        }

        @Test
        @DisplayName("Given a hub.* event, should publish to the dedicated internal hub-events queue")
        void shouldRouteHubEventsToHubQueue() {
            OutboxRelaySchedulerAdapter scheduler = buildScheduler();
            OutboxEntry entry = new OutboxEntry("event-2", "hub.created", "{\"hubId\":\"hub-1\"}",
                    "hub-1", Instant.parse("2026-05-31T10:00:00Z"), 0);
            when(outboxRepository.claimPending(eq(10), argThat(Objects::nonNull), argThat(Objects::nonNull)))
                    .thenReturn(List.of(entry));

            scheduler.relay();

            verify(eventPublisherPort).publish(eq("hub-events-queue"), any(), eq("hub-1"), eq("event-2"));
        }
    }

    private OutboxRelaySchedulerAdapter buildScheduler() {
        OutboxRelaySchedulerAdapter scheduler = new OutboxRelaySchedulerAdapter(outboxRepository, eventPublisherPort, new ObjectMapper());
        ReflectionTestUtils.setField(scheduler, "outboundQueue", "logistics-events-queue");
        ReflectionTestUtils.setField(scheduler, "hubEventsQueue", "hub-events-queue");
        ReflectionTestUtils.setField(scheduler, "batchSize", 10);
        ReflectionTestUtils.setField(scheduler, "maxAttempts", 5);
        ReflectionTestUtils.setField(scheduler, "retryDelayMs", 5000L);
        ReflectionTestUtils.setField(scheduler, "maxRetryDelayMs", 60000L);
        ReflectionTestUtils.setField(scheduler, "processingTimeoutMs", 60000L);
        return scheduler;
    }
}
