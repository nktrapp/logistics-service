package br.furb.logistics.domain.event;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class RouteCalculatedEvent implements DomainEvent {

    private static final String EVENT_TYPE = "route.calculated";

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
        private final String destinationCep;
        private final String routeId;
        private final HubInfo originHub;
        private final HubInfo destinationHub;
        private final List<HopInfo> hops;
        private final double totalDistanceKm;
        private final int estimatedTransitHours;
    }

    @Getter
    @Builder
    public static class HubInfo {
        private final String id;
        private final String name;
        private final String city;
        private final String state;
    }

    @Getter
    @Builder
    public static class HopInfo {
        private final String hubId;
        private final String name;
        private final int order;
    }
}
