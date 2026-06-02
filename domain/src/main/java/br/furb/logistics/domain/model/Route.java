package br.furb.logistics.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public class Route {

    private final String id;
    private final String packageId;
    private final String originHubId;
    private final String destinationHubId;
    private final List<RouteHop> hops;
    private final double totalDistanceKm;
    private final int estimatedTransitHours;
    private final RouteStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class RouteHop {
        private final String hubId;
        private final String hubName;
        private final int order;
    }
}
