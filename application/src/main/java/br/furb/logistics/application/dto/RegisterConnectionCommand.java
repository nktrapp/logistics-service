package br.furb.logistics.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

@Schema(description = "Dados para cadastro de uma conexão entre dois hubs")
public record RegisterConnectionCommand(
        @Schema(description = "Identificador do hub de origem", example = "665f1b2c9a3e4d0012ab34cd")
        @NotBlank String originHubId,
        @Schema(description = "Identificador do hub de destino", example = "665f1b2c9a3e4d0012ab34ce")
        @NotBlank String destinationHubId,
        @Schema(description = "Distância entre os hubs em quilômetros", example = "120.5")
        @NotNull @Positive BigDecimal distanceKm,
        @Schema(description = "Tempo de trânsito estimado em horas", example = "2")
        @NotNull @Positive Integer transitTimeHours
) {}
