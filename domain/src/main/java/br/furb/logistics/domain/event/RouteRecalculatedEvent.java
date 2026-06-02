package br.furb.logistics.domain.event;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class RouteRecalculatedEvent implements DomainEvent {

    private static final String EVENT_TYPE = "route.recalculated";

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
        return payload.getPackageId();
    }

    @Getter
    @Builder
    public static class Payload {
        private final String packageId;
        private final String routeId;
        private final String reason;
        private final RouteCalculatedEvent.HubInfo originHub;
        private final RouteCalculatedEvent.HubInfo destinationHub;
        private final List<RouteCalculatedEvent.HopInfo> hops;
        private final double totalDistanceKm;
        private final int estimatedTransitHours;
    }
}
