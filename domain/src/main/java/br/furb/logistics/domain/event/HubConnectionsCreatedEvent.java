package br.furb.logistics.domain.event;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class HubConnectionsCreatedEvent implements DomainEvent {

    private static final String EVENT_TYPE = "hub.connections.created";

    @Builder.Default
    private final String eventId = UUID.randomUUID().toString();

    @Builder.Default
    private final Instant occurredAt = Instant.now();

    private final Payload payload;

    @Override
    public String getEventType() {
        return EVENT_TYPE;
    }

    @Override
    public String getPartitionKey() {
        return payload.getHubId();
    }

    @Getter
    @Builder
    public static class Payload {
        private final String hubId;
        private final List<String> neighborHubIds;
    }
}
