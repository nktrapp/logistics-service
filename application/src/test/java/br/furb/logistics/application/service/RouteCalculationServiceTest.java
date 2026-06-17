package br.furb.logistics.application.service;

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
        @DisplayName("Given the nearest origin and destination are reachable, should prefer them over a shorter path from a farther candidate")
        void shouldPreferNearestCandidatesOverShorterHubPath() {
            Hub originOne = buildHub("origin-1", "Origin One");
            Hub originTwo = buildHub("origin-2", "Origin Two");
            Hub destinationOne = buildHub("destination-1", "Destination One");
            Hub destinationTwo = buildHub("destination-2", "Destination Two");

            List<HubConnection> connections = List.of(
                    buildConnection("origin-1", "destination-1", 15, 3),
                    buildConnection("origin-2", "destination-1", 5, 1),
                    buildConnection("origin-1", "destination-2", 20, 4)
            );

            RouteCalculationService.SelectedRoute selectedRoute = routeCalculationService.selectBestRoute(
                    List.of(originOne, originTwo),
                    List.of(destinationOne, destinationTwo),
                    List.of(originOne, originTwo, destinationOne, destinationTwo),
                    connections
            );

            assertThat(selectedRoute.originHub().getId()).isEqualTo("origin-1");
            assertThat(selectedRoute.destinationHub().getId()).isEqualTo("destination-1");
            assertThat(selectedRoute.routeResult().totalDistanceKm()).isEqualTo(15.0);
        }

        @Test
        @DisplayName("Given the nearest origin has no usable route, should fall back to the next-nearest origin candidate")
        void shouldFallBackToNextNearestOriginWhenNearestIsUnreachable() {
            Hub originOne = buildHub("origin-1", "Origin One");
            Hub originTwo = buildHub("origin-2", "Origin Two");
            Hub destination = buildHub("destination-1", "Destination One");

            List<HubConnection> connections = List.of(
                    buildConnection("origin-2", "destination-1", 5, 1)
            );

            RouteCalculationService.SelectedRoute selectedRoute = routeCalculationService.selectBestRoute(
                    List.of(originOne, originTwo),
                    List.of(destination),
                    List.of(originOne, originTwo, destination),
                    connections
            );

            assertThat(selectedRoute.originHub().getId()).isEqualTo("origin-2");
            assertThat(selectedRoute.destinationHub().getId()).isEqualTo("destination-1");
            assertThat(selectedRoute.routeResult().totalDistanceKm()).isEqualTo(5.0);
        }

        @Test
        @DisplayName("Given a hub appears in both candidate lists, should route between distinct hubs instead of collapsing to a zero-distance self-loop")
        void shouldNotCollapseToSelfLoopWhenCandidateListsOverlap() {
            Hub hubA = buildHub("hub-a", "Hub A");
            Hub hubB = buildHub("hub-b", "Hub B");

            List<HubConnection> connections = List.of(
                    buildConnection("hub-a", "hub-b", 79, 2)
            );

            RouteCalculationService.SelectedRoute selectedRoute = routeCalculationService.selectBestRoute(
                    List.of(hubA, hubB),
                    List.of(hubB, hubA),
                    List.of(hubA, hubB),
                    connections
            );

            assertThat(selectedRoute.originHub().getId()).isEqualTo("hub-a");
            assertThat(selectedRoute.destinationHub().getId()).isEqualTo("hub-b");
            assertThat(selectedRoute.originHub().getId()).isNotEqualTo(selectedRoute.destinationHub().getId());
            assertThat(selectedRoute.routeResult().totalDistanceKm()).isEqualTo(79.0);
        }

        @Test
        @DisplayName("Given sender and recipient resolve to the same nearest hub, should return a single-hub local route")
        void shouldReturnSingleHubRouteWhenSenderAndRecipientShareNearestHub() {
            Hub hub = buildHub("hub-x", "Hub X");

            RouteCalculationService.SelectedRoute selectedRoute = routeCalculationService.selectBestRoute(
                    List.of(hub),
                    List.of(hub),
                    List.of(hub),
                    List.of()
            );

            assertThat(selectedRoute.originHub().getId()).isEqualTo("hub-x");
            assertThat(selectedRoute.destinationHub().getId()).isEqualTo("hub-x");
            assertThat(selectedRoute.routeResult().totalDistanceKm()).isEqualTo(0.0);
            assertThat(selectedRoute.routeResult().path()).containsExactly("hub-x");
        }

        @Test
        @DisplayName("Given distinct candidates with no connection between them, should throw when no route can be selected")
        void shouldThrowExceptionWhenNoCandidateRouteIsReachable() {
            Hub origin = buildHub("origin-1", "Origin");
            Hub destination = buildHub("destination-1", "Destination");

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
