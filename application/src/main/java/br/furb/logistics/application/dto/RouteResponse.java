package br.furb.logistics.application.dto;

import br.furb.logistics.domain.model.Route;
import br.furb.logistics.domain.model.RouteStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "Rota calculada para um pacote")
public record RouteResponse(
        @Schema(description = "Identificador da rota", example = "665f1b2c9a3e4d0012ab34cd")
        String id,
        @Schema(description = "Identificador do pacote", example = "665f1b2c9a3e4d0012ab34ce")
        String packageId,
        @Schema(description = "Identificador do hub de origem")
        String originHubId,
        @Schema(description = "Identificador do hub de destino")
        String destinationHubId,
        @Schema(description = "Sequência de saltos (hops) entre hubs que compõem a rota")
        List<Route.RouteHop> hops,
        @Schema(description = "Distância total da rota em quilômetros", example = "350.0")
        double totalDistanceKm,
        @Schema(description = "Tempo total de trânsito estimado em horas", example = "6")
        int estimatedTransitHours,
        @Schema(description = "Status da rota")
        RouteStatus status,
        @Schema(description = "Data/hora do cálculo da rota")
        Instant createdAt
) {}
