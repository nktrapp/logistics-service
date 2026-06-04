package br.furb.logistics.application.service;

import br.furb.logistics.domain.exception.RouteCalculationException;
import br.furb.logistics.domain.model.Hub;
import br.furb.logistics.domain.model.HubConnection;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Implementa Dijkstra sobre o grafo de hubs para encontrar a rota de menor distância.
 */
@Slf4j
public class RouteCalculationService {

    public SelectedRoute selectBestRoute(List<Hub> originCandidates,
                                         List<Hub> destinationCandidates,
                                         List<Hub> allHubs,
                                         List<HubConnection> allConnections) {
        SelectedRoute bestRoute = null;

        for (Hub originCandidate : sortCandidates(originCandidates)) {
            for (Hub destinationCandidate : sortCandidates(destinationCandidates)) {
                try {
                    RouteResult routeResult = calculateShortestRoute(
                            originCandidate.getId(),
                            destinationCandidate.getId(),
                            allHubs,
                            allConnections
                    );

                    SelectedRoute candidateRoute = new SelectedRoute(originCandidate, destinationCandidate, routeResult);
                    if (isBetter(candidateRoute, bestRoute)) {
                        bestRoute = candidateRoute;
                    }
                } catch (RouteCalculationException e) {
                    log.debug("[route-calc] Candidate route from {} to {} is not reachable",
                            originCandidate.getId(), destinationCandidate.getId());
                }
            }
        }

        if (bestRoute == null) {
            throw new RouteCalculationException("No route found for available hub candidates");
        }

        return bestRoute;
    }

    public RouteResult calculateShortestRoute(String originHubId, String destinationHubId,
                                              List<Hub> allHubs, List<HubConnection> allConnections) {
        log.info("[route-calc] Calculating route from hub {} to hub {}", originHubId, destinationHubId);

        Map<String, List<Edge>> adjacency = buildAdjacencyList(allConnections);
        Map<String, Hub> hubMap = new HashMap<>();
        for (Hub hub : allHubs) {
            hubMap.put(hub.getId(), hub);
        }

        Map<String, Double> distances = new HashMap<>();
        Map<String, String> previous = new HashMap<>();
        Map<String, Integer> transitTimes = new HashMap<>();
        Set<String> visited = new HashSet<>();

        for (Hub hub : allHubs) {
            distances.put(hub.getId(), Double.MAX_VALUE);
            transitTimes.put(hub.getId(), Integer.MAX_VALUE);
        }
        distances.put(originHubId, 0.0);
        transitTimes.put(originHubId, 0);

        PriorityQueue<NodeDistance> queue = new PriorityQueue<>(Comparator.comparingDouble(NodeDistance::distance));
        queue.add(new NodeDistance(originHubId, 0.0));

        while (!queue.isEmpty()) {
            NodeDistance current = queue.poll();

            if (visited.contains(current.hubId())) {
                continue;
            }
            visited.add(current.hubId());

            if (current.hubId().equals(destinationHubId)) {
                break;
            }

            List<Edge> neighbors = adjacency.getOrDefault(current.hubId(), Collections.emptyList());
            for (Edge edge : neighbors) {
                // Skip edges that point at a hub which is not part of the routable set
                // (e.g. an inactive or removed hub still referenced by a connection).
                if (!distances.containsKey(edge.destinationHubId())) {
                    continue;
                }
                if (visited.contains(edge.destinationHubId())) {
                    continue;
                }

                double newDistance = distances.get(current.hubId()) + edge.distanceKm();
                if (newDistance < distances.get(edge.destinationHubId())) {
                    distances.put(edge.destinationHubId(), newDistance);
                    transitTimes.put(edge.destinationHubId(), transitTimes.get(current.hubId()) + edge.transitTimeHours());
                    previous.put(edge.destinationHubId(), current.hubId());
                    queue.add(new NodeDistance(edge.destinationHubId(), newDistance));
                }
            }
        }

        Double totalDistance = distances.get(destinationHubId);
        if (totalDistance == Double.MAX_VALUE) {
            throw new RouteCalculationException("No route found from hub " + originHubId + " to hub " + destinationHubId);
        }

        List<String> path = reconstructPath(previous, originHubId, destinationHubId);
        int totalTransitHours = transitTimes.get(destinationHubId);

        log.info("[route-calc] Route calculated: {} hops, {} km, {} hours", path.size(), totalDistance, totalTransitHours);

        return new RouteResult(path, totalDistance, totalTransitHours);
    }

    private Map<String, List<Edge>> buildAdjacencyList(List<HubConnection> connections) {
        Map<String, List<Edge>> adjacency = new HashMap<>();
        for (HubConnection conn : connections) {
            double distance = conn.getDistanceKm().doubleValue();
            int transitTime = conn.getTransitTimeHours();

            adjacency.computeIfAbsent(conn.getOriginHubId(), k -> new ArrayList<>())
                    .add(new Edge(conn.getDestinationHubId(), distance, transitTime));

            adjacency.computeIfAbsent(conn.getDestinationHubId(), k -> new ArrayList<>())
                    .add(new Edge(conn.getOriginHubId(), distance, transitTime));
        }
        return adjacency;
    }

    private List<String> reconstructPath(Map<String, String> previous, String origin, String destination) {
        List<String> path = new ArrayList<>();
        String current = destination;

        while (current != null) {
            path.add(current);
            if (current.equals(origin)) {
                break;
            }
            current = previous.get(current);
        }

        Collections.reverse(path);
        return path;
    }

    private List<Hub> sortCandidates(List<Hub> candidates) {
        return candidates.stream()
                .sorted(Comparator.comparing(Hub::getName)
                        .thenComparing(Hub::getCity)
                        .thenComparing(Hub::getState)
                        .thenComparing(Hub::getId))
                .toList();
    }

    private boolean isBetter(SelectedRoute candidate, SelectedRoute currentBest) {
        if (currentBest == null) {
            return true;
        }

        int distanceComparison = Double.compare(
                candidate.routeResult().totalDistanceKm(),
                currentBest.routeResult().totalDistanceKm()
        );
        if (distanceComparison != 0) {
            return distanceComparison < 0;
        }

        int transitComparison = Integer.compare(
                candidate.routeResult().totalTransitHours(),
                currentBest.routeResult().totalTransitHours()
        );
        if (transitComparison != 0) {
            return transitComparison < 0;
        }

        int originNameComparison = candidate.originHub().getName().compareTo(currentBest.originHub().getName());
        if (originNameComparison != 0) {
            return originNameComparison < 0;
        }

        int destinationNameComparison = candidate.destinationHub().getName().compareTo(currentBest.destinationHub().getName());
        if (destinationNameComparison != 0) {
            return destinationNameComparison < 0;
        }

        int originIdComparison = candidate.originHub().getId().compareTo(currentBest.originHub().getId());
        if (originIdComparison != 0) {
            return originIdComparison < 0;
        }

        return candidate.destinationHub().getId().compareTo(currentBest.destinationHub().getId()) < 0;
    }

    public record RouteResult(List<String> path, double totalDistanceKm, int totalTransitHours) {}

    public record SelectedRoute(Hub originHub, Hub destinationHub, RouteResult routeResult) {}

    private record NodeDistance(String hubId, double distance) {}

    private record Edge(String destinationHubId, double distanceKm, int transitTimeHours) {}
}
