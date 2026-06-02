package br.furb.logistics.infrastructure.persistence.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("routes")
public class RouteDocument {

    @Id
    private String id;

    @Indexed(unique = true)
    private String packageId;

    private String originHubId;
    private String destinationHubId;
    private List<RouteHopEmbedded> hops;
    private double totalDistanceKm;
    private int estimatedTransitHours;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RouteHopEmbedded {
        private String hubId;
        private String hubName;
        private int order;
    }
}
