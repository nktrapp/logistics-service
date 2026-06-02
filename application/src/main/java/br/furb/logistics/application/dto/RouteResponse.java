package br.furb.logistics.application.dto;

import br.furb.logistics.domain.model.Route;
import br.furb.logistics.domain.model.RouteStatus;

import java.time.Instant;
import java.util.List;

public record RouteResponse(
        String id,
        String packageId,
        String originHubId,
        String destinationHubId,
        List<Route.RouteHop> hops,
        double totalDistanceKm,
        int estimatedTransitHours,
        RouteStatus status,
        Instant createdAt
) {}
