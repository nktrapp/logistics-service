package br.furb.logistics.infrastructure.adapter.out.persistence.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("hub_connections")
public class HubConnectionDocument {

    @Id
    private String id;
    private String originHubId;
    private String destinationHubId;
    private BigDecimal distanceKm;
    private Integer transitTimeHours;
}
