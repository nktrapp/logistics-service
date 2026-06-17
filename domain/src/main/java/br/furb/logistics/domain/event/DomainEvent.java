package br.furb.logistics.domain.event;

import java.time.Instant;

public interface DomainEvent {

    String getEventId();

    String getEventType();

    Instant getOccurredAt();

    Object getPayload();

    String getPartitionKey();
}
