package br.furb.logistics.core.service;

import br.furb.logistics.domain.exception.RouteCalculationException;
import br.furb.logistics.domain.model.Hub;
import br.furb.logistics.domain.model.HubConnection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RouteCalculationService")
class RouteCalculationServiceTest {

    RouteCalculationService routeCalculationService = new RouteCalculationService();

    @Nested
    @DisplayName("Best route selection")
    class BestRouteSelection {

        @Test
        @DisplayName("Given multiple candidate pairs, should select the shortest reachable route")
        void shouldSelectShortestReachableRouteWhenMultipleCandidatesAreAvailable() {
            // GIVEN
            Hub originOne = buildHub("origin-1", "Origin One");
            Hub originTwo = buildHub("origin-2", "Origin Two");
            Hub destinationOne = buildHub("destination-1", "Destination One");
            Hub destinationTwo = buildHub("destination-2", "Destination Two");

            List<HubConnection> connections = List.of(
                    buildConnection("origin-1", "destination-1", 15, 3),
                    buildConnection("origin-2", "destination-1", 5, 1),
                    buildConnection("origin-1", "destination-2", 20, 4)
            );

            // WHEN
            RouteCalculationService.SelectedRoute selectedRoute = routeCalculationService.selectBestRoute(
                    List.of(originOne, originTwo),
                    List.of(destinationOne, destinationTwo),
                    List.of(originOne, originTwo, destinationOne, destinationTwo),
                    connections
            );

            // THEN
            assertThat(selectedRoute.originHub().getId()).isEqualTo("origin-2");
            assertThat(selectedRoute.destinationHub().getId()).isEqualTo("destination-1");
            assertThat(selectedRoute.routeResult().totalDistanceKm()).isEqualTo(5.0);
            assertThat(selectedRoute.routeResult().path()).containsExactly("origin-2", "destination-1");
        }

        @Test
        @DisplayName("Given tied route metrics, should choose the deterministic lexicographic candidate")
        void shouldChooseDeterministicCandidateWhenRouteMetricsTie() {
            // GIVEN
            Hub alphaOrigin = buildHub("origin-a", "Alpha Origin");
            Hub betaOrigin = buildHub("origin-b", "Beta Origin");
            Hub destination = buildHub("destination-1", "Destination");

            List<HubConnection> connections = List.of(
                    buildConnection("origin-a", "destination-1", 10, 2),
                    buildConnection("origin-b", "destination-1", 10, 2)
            );

            // WHEN
            RouteCalculationService.SelectedRoute selectedRoute = routeCalculationService.selectBestRoute(
                    List.of(betaOrigin, alphaOrigin),
                    List.of(destination),
                    List.of(alphaOrigin, betaOrigin, destination),
                    connections
            );

            // THEN
            assertThat(selectedRoute.originHub().getId()).isEqualTo("origin-a");
            assertThat(selectedRoute.routeResult().totalDistanceKm()).isEqualTo(10.0);
        }

        @Test
        @DisplayName("Given unreachable candidates, should throw exception when no route can be selected")
        void shouldThrowExceptionWhenNoCandidateRouteIsReachable() {
            // GIVEN
            Hub origin = buildHub("origin-1", "Origin");
            Hub destination = buildHub("destination-1", "Destination");

            // WHEN / THEN
            assertThatThrownBy(() -> routeCalculationService.selectBestRoute(
                    List.of(origin),
                    List.of(destination),
                    List.of(origin, destination),
                    List.of()
            )).isInstanceOf(RouteCalculationException.class)
                    .hasMessage("No route found for available hub candidates");
        }
    }

    private Hub buildHub(String id, String name) {
        return Hub.builder()
                .id(id)
                .name(name)
                .cep("89000000")
                .city("Blumenau")
                .state("SC")
                .active(true)
                .build();
    }

    private HubConnection buildConnection(String originHubId, String destinationHubId, int distanceKm, int transitHours) {
        return HubConnection.builder()
                .originHubId(originHubId)
                .destinationHubId(destinationHubId)
                .distanceKm(BigDecimal.valueOf(distanceKm))
                .transitTimeHours(transitHours)
                .build();
    }
}
