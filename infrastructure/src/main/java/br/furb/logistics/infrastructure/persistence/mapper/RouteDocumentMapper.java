package br.furb.logistics.infrastructure.persistence.mapper;

import br.furb.logistics.domain.model.Route;
import br.furb.logistics.domain.model.RouteStatus;
import br.furb.logistics.infrastructure.persistence.document.RouteDocument;

import java.util.Collections;
import java.util.List;

import static java.util.Objects.isNull;

public class RouteDocumentMapper {

    private RouteDocumentMapper() {}

    public static RouteDocument toDocument(Route route) {
        List<RouteDocument.RouteHopEmbedded> hops = isNull(route.getHops()) ? Collections.emptyList() :
                route.getHops().stream()
                        .map(hop -> RouteDocument.RouteHopEmbedded.builder()
                                .hubId(hop.getHubId())
                                .hubName(hop.getHubName())
                                .order(hop.getOrder())
                                .build())
                        .toList();

        return RouteDocument.builder()
                .id(route.getId())
                .packageId(route.getPackageId())
                .originHubId(route.getOriginHubId())
                .destinationHubId(route.getDestinationHubId())
                .hops(hops)
                .totalDistanceKm(route.getTotalDistanceKm())
                .estimatedTransitHours(route.getEstimatedTransitHours())
                .status(route.getStatus().name())
                .createdAt(route.getCreatedAt())
                .updatedAt(route.getUpdatedAt())
                .build();
    }

    public static Route toDomain(RouteDocument doc) {
        List<Route.RouteHop> hops = isNull(doc.getHops()) ? Collections.emptyList() :
                doc.getHops().stream()
                        .map(hop -> Route.RouteHop.builder()
                                .hubId(hop.getHubId())
                                .hubName(hop.getHubName())
                                .order(hop.getOrder())
                                .build())
                        .toList();

        return Route.builder()
                .id(doc.getId())
                .packageId(doc.getPackageId())
                .originHubId(doc.getOriginHubId())
                .destinationHubId(doc.getDestinationHubId())
                .hops(hops)
                .totalDistanceKm(doc.getTotalDistanceKm())
                .estimatedTransitHours(doc.getEstimatedTransitHours())
                .status(RouteStatus.valueOf(doc.getStatus()))
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .build();
    }
}
