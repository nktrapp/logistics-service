package br.furb.logistics.application.usecase;

import br.furb.logistics.application.dto.RouteResponse;
import br.furb.logistics.domain.model.Route;
import br.furb.logistics.domain.model.RouteStatus;
import br.furb.logistics.domain.port.RouteRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetRouteUseCase")
class GetRouteUseCaseTest {

    @Mock
    RouteRepositoryPort routeRepository;

    @Test
    @DisplayName("Given an existing route id, should return the mapped response")
    void shouldFindById() {
        GetRouteUseCase useCase = new GetRouteUseCase(routeRepository);
        when(routeRepository.findById("route-1")).thenReturn(Optional.of(buildRoute()));

        Optional<RouteResponse> response = useCase.findById("route-1");

        assertThat(response).isPresent();
    }

    @Test
    @DisplayName("Given an unknown route id, should return empty")
    void shouldReturnEmptyWhenRouteMissing() {
        GetRouteUseCase useCase = new GetRouteUseCase(routeRepository);
        when(routeRepository.findById("missing")).thenReturn(Optional.empty());

        Optional<RouteResponse> response = useCase.findById("missing");

        assertThat(response).isEmpty();
    }

    @Test
    @DisplayName("Given an existing package id, should return the mapped route response")
    void shouldFindByPackageId() {
        GetRouteUseCase useCase = new GetRouteUseCase(routeRepository);
        when(routeRepository.findByPackageId("pkg-1")).thenReturn(Optional.of(buildRoute()));

        Optional<RouteResponse> response = useCase.findByPackageId("pkg-1");

        assertThat(response).isPresent();
    }

    private Route buildRoute() {
        return Route.builder()
                .id("route-1")
                .packageId("pkg-1")
                .originHubId("origin-1")
                .destinationHubId("dest-1")
                .hops(List.of())
                .totalDistanceKm(50.0)
                .estimatedTransitHours(6)
                .status(RouteStatus.CALCULATED)
                .createdAt(Instant.parse("2026-05-31T10:00:00Z"))
                .build();
    }
}
