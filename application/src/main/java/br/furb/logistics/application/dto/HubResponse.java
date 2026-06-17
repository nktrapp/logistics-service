package br.furb.logistics.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Representação de um hub logístico")
public record HubResponse(
        @Schema(description = "Identificador do hub", example = "665f1b2c9a3e4d0012ab34cd")
        String id,
        @Schema(description = "Nome do hub", example = "Hub Blumenau")
        String name,
        @Schema(description = "CEP do hub", example = "89010000")
        String cep,
        @Schema(description = "Cidade resolvida pelo CEP", example = "Blumenau")
        String city,
        @Schema(description = "UF resolvida pelo CEP", example = "SC")
        String state,
        @Schema(description = "Latitude do hub", example = "-26.9194")
        double latitude,
        @Schema(description = "Longitude do hub", example = "-49.0661")
        double longitude,
        @Schema(description = "Indica se o hub está ativo", example = "true")
        boolean active
) {}
