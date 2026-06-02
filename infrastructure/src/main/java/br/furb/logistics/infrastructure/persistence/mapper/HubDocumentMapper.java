package br.furb.logistics.infrastructure.persistence.mapper;

import br.furb.logistics.domain.model.Hub;
import br.furb.logistics.infrastructure.persistence.document.HubDocument;

public class HubDocumentMapper {

    private HubDocumentMapper() {}

    public static HubDocument toDocument(Hub hub) {
        return HubDocument.builder()
                .id(hub.getId())
                .name(hub.getName())
                .cep(hub.getCep())
                .city(hub.getCity())
                .state(hub.getState())
                .latitude(hub.getLatitude())
                .longitude(hub.getLongitude())
                .active(hub.isActive())
                .build();
    }

    public static Hub toDomain(HubDocument doc) {
        return Hub.builder()
                .id(doc.getId())
                .name(doc.getName())
                .cep(doc.getCep())
                .city(doc.getCity())
                .state(doc.getState())
                .latitude(doc.getLatitude())
                .longitude(doc.getLongitude())
                .active(doc.isActive())
                .build();
    }
}
