package br.furb.logistics.infrastructure.persistence.mapper;

import br.furb.logistics.domain.model.HubConnection;
import br.furb.logistics.infrastructure.persistence.document.HubConnectionDocument;

public class HubConnectionDocumentMapper {

    private HubConnectionDocumentMapper() {}

    public static HubConnectionDocument toDocument(HubConnection conn) {
        return HubConnectionDocument.builder()
                .id(conn.getId())
                .originHubId(conn.getOriginHubId())
                .destinationHubId(conn.getDestinationHubId())
                .distanceKm(conn.getDistanceKm())
                .transitTimeHours(conn.getTransitTimeHours())
                .build();
    }

    public static HubConnection toDomain(HubConnectionDocument doc) {
        return HubConnection.builder()
                .id(doc.getId())
                .originHubId(doc.getOriginHubId())
                .destinationHubId(doc.getDestinationHubId())
                .distanceKm(doc.getDistanceKm())
                .transitTimeHours(doc.getTransitTimeHours())
                .build();
    }
}
