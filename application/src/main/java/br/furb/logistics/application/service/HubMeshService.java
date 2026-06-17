package br.furb.logistics.application.service;

import br.furb.logistics.domain.model.Coordinates;
import br.furb.logistics.domain.model.Hub;
import br.furb.logistics.domain.model.HubConnection;
import br.furb.logistics.domain.util.HaversineCalculator;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
public class HubMeshService {

    private static final BigDecimal MIN_DISTANCE_KM = new BigDecimal("0.10");

    public List<HubConnection> computeConnections(Hub newHub,
                                                  List<Hub> allHubs,
                                                  List<HubConnection> existingConnections,
                                                  int neighbors,
                                                  int averageSpeedKmh) {
        if (!hasCoordinates(newHub)) {
            log.warn("[hub-mesh] Hub {} has no coordinates; skipping automatic mesh generation", newHub.getId());
            return List.of();
        }

        Set<String> existingPairs = new HashSet<>();
        for (HubConnection connection : existingConnections) {
            existingPairs.add(pairKey(connection.getOriginHubId(), connection.getDestinationHubId()));
        }

        Coordinates origin = new Coordinates(newHub.getLatitude(), newHub.getLongitude());

        List<RankedCandidate> rankedCandidates = new ArrayList<>();
        for (Hub candidate : allHubs) {
            if (candidate.getId().equals(newHub.getId()) || !hasCoordinates(candidate)) {
                continue;
            }
            double distance = HaversineCalculator.distanceKm(origin,
                    new Coordinates(candidate.getLatitude(), candidate.getLongitude()));
            rankedCandidates.add(new RankedCandidate(candidate, distance));
        }

        rankedCandidates.sort(Comparator
                .comparingDouble(RankedCandidate::distance)
                .thenComparing(ranked -> ranked.hub().getId()));

        List<HubConnection> connections = new ArrayList<>();
        for (RankedCandidate ranked : rankedCandidates) {
            if (connections.size() >= neighbors) {
                break;
            }
            if (existingPairs.contains(pairKey(newHub.getId(), ranked.hub().getId()))) {
                continue;
            }
            connections.add(buildConnection(newHub.getId(), ranked.hub().getId(), ranked.distance(), averageSpeedKmh));
        }

        return connections;
    }

    private HubConnection buildConnection(String originHubId, String destinationHubId,
                                          double distanceKm, int averageSpeedKmh) {
        BigDecimal roundedDistance = BigDecimal.valueOf(distanceKm).setScale(2, RoundingMode.HALF_UP);
        if (roundedDistance.compareTo(MIN_DISTANCE_KM) < 0) {
            roundedDistance = MIN_DISTANCE_KM;
        }
        int transitTimeHours = Math.max(1, (int) Math.ceil(distanceKm / averageSpeedKmh));

        return HubConnection.builder()
                .originHubId(originHubId)
                .destinationHubId(destinationHubId)
                .distanceKm(roundedDistance)
                .transitTimeHours(transitTimeHours)
                .build();
    }

    private boolean hasCoordinates(Hub hub) {
        return hub.getLatitude() != 0.0 || hub.getLongitude() != 0.0;
    }

    private String pairKey(String firstHubId, String secondHubId) {
        if (firstHubId.compareTo(secondHubId) <= 0) {
            return firstHubId + "|" + secondHubId;
        }
        return secondHubId + "|" + firstHubId;
    }

    private record RankedCandidate(Hub hub, double distance) {}
}
