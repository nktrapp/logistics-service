package br.furb.logistics.domain.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@AllArgsConstructor
public class HubConnection {

    private final String id;

    @NotBlank
    private final String originHubId;

    @NotBlank
    private final String destinationHubId;

    @NotNull
    @Positive
    private final BigDecimal distanceKm;

    @NotNull
    @Positive
    private final Integer transitTimeHours;
}
