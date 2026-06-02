package br.furb.logistics.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record RegisterConnectionCommand(
        @NotBlank String originHubId,
        @NotBlank String destinationHubId,
        @NotNull @Positive BigDecimal distanceKm,
        @NotNull @Positive Integer transitTimeHours
) {}
