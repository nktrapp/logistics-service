package br.furb.logistics.application.service;

import br.furb.logistics.domain.exception.RouteCalculationException;
import br.furb.logistics.domain.model.Hub;
import br.furb.logistics.domain.model.HubConnection;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.stream.Stream;

@Slf4j
public class RouteCalculationService {

    private static final String NO_ROUTE_AVAILABLE = "No route found for available hub candidates";

    private static final Comparator<CandidateRoute> BY_PROXIMITY_THEN_COST =
            Comparator.comparingInt(CandidateRoute::proximityRank)
                    .thenComparingDouble(route -> route.routeResult().totalDistanceKm())
                    .thenComparingInt(route -> route.routeResult().totalTransitHours())
                    .thenComparing(route -> route.origin().getName())
                    .thenComparing(route -> route.destination().getName())
                    .thenComparing(route -> route.origin().getId())
                    .thenComparing(route -> route.destination().getId());

    public SelectedRoute selectBestRoute(List<Hub> originCandidates,
                                         List<Hub> destinationCandidates,
                                         List<Hub> allHubs,
                                         List<HubConnection> allConnections) {
        if (originCandidates.isEmpty() || destinationCandidates.isEmpty()) {
            throw new RouteCalculationException(NO_ROUTE_AVAILABLE);
        }

        Hub nearestOrigin = originCandidates.get(0);
        Hub nearestDestination = destinationCandidates.get(0);
        if (nearestOrigin.getId().equals(nearestDestination.getId())) {
            return localDelivery(nearestOrigin, allHubs, allConnections);
        }

        return transportRoutes(originCandidates, destinationCandidates, allHubs, allConnections)
                .min(BY_PROXIMITY_THEN_COST)
                .map(CandidateRoute::toSelectedRoute)
                .orElseThrow(() -> new RouteCalculationException(NO_ROUTE_AVAILABLE));
    }

    private SelectedRoute localDelivery(Hub hub, List<Hub> allHubs, List<HubConnection> allConnections) {
        RouteResult route = calculateShortestRoute(hub.getId(), hub.getId(), allHubs, allConnections);
        return new SelectedRoute(hub, hub, route);
    }

    private Stream<CandidateRoute> transportRoutes(List<Hub> originCandidates,
                                                   List<Hub> destinationCandidates,
                                                   List<Hub> allHubs,
                                                   List<HubConnection> allConnections) {
        List<CandidateRoute> routes = new ArrayList<>();
        for (int originRank = 0; originRank < originCandidates.size(); originRank++) {
            Hub origin = originCandidates.get(originRank);
            for (int destinationRank = 0; destinationRank < destinationCandidates.size(); destinationRank++) {
                Hub destination = destinationCandidates.get(destinationRank);
                if (isSameHub(origin, destination)) {
                    continue;
                }
                int proximityRank = originRank + destinationRank;
                reachableRoute(origin, destination, proximityRank, allHubs, allConnections).ifPresent(routes::add);
            }
        }
        return routes.stream();
    }

    private Optional<CandidateRoute> reachableRoute(Hub origin, Hub destination, int proximityRank,
                                                    List<Hub> allHubs, List<HubConnection> allConnections) {
        try {
            RouteResult route = calculateShortestRoute(origin.getId(), destination.getId(), allHubs, allConnections);
            return Optional.of(new CandidateRoute(origin, destination, proximityRank, route));
        } catch (RouteCalculationException notReachable) {
            log.debug("[route-calc] Candidate route from {} to {} is not reachable", origin.getId(), destination.getId());
            return Optional.empty();
        }
    }

    private boolean isSameHub(Hub first, Hub second) {
        return first.getId().equals(second.getId());
    }

    public RouteResult calculateShortestRoute(String originHubId, String destinationHubId,
                                              List<Hub> allHubs, List<HubConnection> allConnections) {
        log.info("[route-calc] Calculating route from hub {} to hub {}", originHubId, destinationHubId);

        Map<String, List<Edge>> adjacency = buildAdjacencyList(allConnections);

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

    public record RouteResult(List<String> path, double totalDistanceKm, int totalTransitHours) {}

    public record SelectedRoute(Hub originHub, Hub destinationHub, RouteResult routeResult) {}

    private record CandidateRoute(Hub origin, Hub destination, int proximityRank, RouteResult routeResult) {
        SelectedRoute toSelectedRoute() {
            return new SelectedRoute(origin, destination, routeResult);
        }
    }

    private record NodeDistance(String hubId, double distance) {}

    private record Edge(String destinationHubId, double distanceKm, int transitTimeHours) {}
}
