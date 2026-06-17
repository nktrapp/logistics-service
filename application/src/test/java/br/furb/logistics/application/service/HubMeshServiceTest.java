package br.furb.logistics.application.service;

import br.furb.logistics.domain.model.Hub;
import br.furb.logistics.domain.model.HubConnection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HubMeshService")
class HubMeshServiceTest {

    private static final int K = 3;
    private static final int AVG_SPEED_KMH = 60;

    HubMeshService hubMeshService = new HubMeshService();

    @Test
    @DisplayName("Given several hubs, should connect the new hub to its K nearest neighbours only")
    void shouldConnectToKNearestNeighbours() {
        Hub newHub = hub("blu", -26.92, -49.07);
        List<Hub> allHubs = List.of(
                newHub,
                hub("joi", -26.30, -48.85),   // closest
                hub("fln", -27.59, -48.55),
                hub("cwb", -25.43, -49.27),
                hub("sao", -23.55, -46.63));  // farthest

        List<HubConnection> connections = hubMeshService.computeConnections(newHub, allHubs, List.of(), K, AVG_SPEED_KMH);

        assertThat(connections).hasSize(3);
        assertThat(connections).allSatisfy(connection -> {
            assertThat(connection.getOriginHubId()).isEqualTo("blu");
            assertThat(connection.getDistanceKm()).isGreaterThan(BigDecimal.ZERO);
            assertThat(connection.getTransitTimeHours()).isGreaterThanOrEqualTo(1);
        });
        assertThat(connections).extracting(HubConnection::getDestinationHubId)
                .containsExactlyInAnyOrder("joi", "fln", "cwb")
                .doesNotContain("sao");
    }

    @Test
    @DisplayName("Given fewer candidates than K, should cap the number of connections to the available candidates")
    void shouldCapToAvailableCandidates() {
        Hub newHub = hub("blu", -26.92, -49.07);
        List<HubConnection> connections = hubMeshService.computeConnections(
                newHub, List.of(newHub, hub("joi", -26.30, -48.85)), List.of(), K, AVG_SPEED_KMH);

        assertThat(connections).hasSize(1);
        assertThat(connections.getFirst().getDestinationHubId()).isEqualTo("joi");
    }

    @Test
    @DisplayName("Given the very first hub, should produce no connections")
    void shouldProduceNoConnectionsForFirstHub() {
        Hub newHub = hub("blu", -26.92, -49.07);
        List<HubConnection> connections = hubMeshService.computeConnections(
                newHub, List.of(newHub), List.of(), K, AVG_SPEED_KMH);

        assertThat(connections).isEmpty();
    }

    @Test
    @DisplayName("Given a new hub without coordinates, should skip mesh generation")
    void shouldSkipWhenNewHubHasNoCoordinates() {
        Hub newHub = hub("blu", 0.0, 0.0);
        List<HubConnection> connections = hubMeshService.computeConnections(
                newHub, List.of(newHub, hub("joi", -26.30, -48.85)), List.of(), K, AVG_SPEED_KMH);

        assertThat(connections).isEmpty();
    }

    @Test
    @DisplayName("Given a candidate without coordinates, should exclude it from neighbour selection")
    void shouldExcludeCandidateWithoutCoordinates() {
        Hub newHub = hub("blu", -26.92, -49.07);
        List<Hub> allHubs = List.of(newHub, hub("nocoord", 0.0, 0.0), hub("joi", -26.30, -48.85));

        List<HubConnection> connections = hubMeshService.computeConnections(newHub, allHubs, List.of(), K, AVG_SPEED_KMH);

        assertThat(connections).extracting(HubConnection::getDestinationHubId).containsExactly("joi");
    }

    @Test
    @DisplayName("Given an already existing pair, should not duplicate the connection")
    void shouldNotDuplicateExistingPair() {
        Hub newHub = hub("blu", -26.92, -49.07);
        List<Hub> allHubs = List.of(newHub, hub("joi", -26.30, -48.85), hub("fln", -27.59, -48.55));
        List<HubConnection> existing = List.of(HubConnection.builder()
                .originHubId("joi").destinationHubId("blu")
                .distanceKm(BigDecimal.valueOf(70)).transitTimeHours(2).build());

        List<HubConnection> connections = hubMeshService.computeConnections(newHub, allHubs, existing, K, AVG_SPEED_KMH);

        assertThat(connections).extracting(HubConnection::getDestinationHubId)
                .containsExactly("fln")
                .doesNotContain("joi");
    }

    @Test
    @DisplayName("Given the candidates in any order, should produce the same connections (deterministic)")
    void shouldBeDeterministicRegardlessOfInputOrder() {
        Hub newHub = hub("blu", -26.92, -49.07);
        List<Hub> ascending = List.of(newHub,
                hub("joi", -26.30, -48.85), hub("fln", -27.59, -48.55),
                hub("cwb", -25.43, -49.27), hub("sao", -23.55, -46.63));
        List<Hub> shuffled = new ArrayList<>(ascending);
        shuffled.sort((a, b) -> b.getId().compareTo(a.getId()));

        List<HubConnection> first = hubMeshService.computeConnections(newHub, ascending, List.of(), K, AVG_SPEED_KMH);
        List<HubConnection> second = hubMeshService.computeConnections(newHub, shuffled, List.of(), K, AVG_SPEED_KMH);

        assertThat(first).extracting(HubConnection::getDestinationHubId)
                .isEqualTo(second.stream().map(HubConnection::getDestinationHubId).toList());
    }

    @Test
    @DisplayName("Given two candidates at the same location and K=1, should break the tie by hub id")
    void shouldBreakDistanceTieByHubId() {
        Hub newHub = hub("origin", -26.0, -49.0);
        List<Hub> allHubs = List.of(newHub, hub("b-hub", -27.0, -48.0), hub("a-hub", -27.0, -48.0));

        List<HubConnection> connections = hubMeshService.computeConnections(newHub, allHubs, List.of(), 1, AVG_SPEED_KMH);

        assertThat(connections).extracting(HubConnection::getDestinationHubId).containsExactly("a-hub");
    }

    private Hub hub(String id, double latitude, double longitude) {
        return Hub.builder()
                .id(id)
                .name("Hub " + id)
                .cep("89000000")
                .city("City " + id)
                .state("SC")
                .latitude(latitude)
                .longitude(longitude)
                .active(true)
                .build();
    }
}
