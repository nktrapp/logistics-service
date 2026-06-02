package br.furb.logistics.domain.event;

import java.time.Instant;

public interface DomainEvent {

    String getEventId();

    String getEventType();

    Instant getOccurredAt();

    Object getPayload();

    /**
     * Aggregate key used as the SQS FIFO MessageGroupId so that all events for the
     * same package are delivered and processed in order. Falls back to the eventId
     * when an event is not bound to a specific aggregate.
     */
    String getPartitionKey();
}
