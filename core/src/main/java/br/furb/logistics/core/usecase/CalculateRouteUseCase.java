package br.furb.logistics.core.usecase;

import br.furb.logistics.core.dto.RouteResponse;
import br.furb.logistics.core.mapper.RouteMapper;
import br.furb.logistics.core.service.RouteCalculationService;
import br.furb.logistics.domain.event.RouteCalculatedEvent;
import br.furb.logistics.domain.exception.CepValidationException;
import br.furb.logistics.domain.model.CepInfo;
import br.furb.logistics.domain.model.Hub;
import br.furb.logistics.domain.model.HubConnection;
import br.furb.logistics.domain.model.Route;
import br.furb.logistics.domain.model.RouteStatus;
import br.furb.logistics.domain.port.CepLookupPort;
import br.furb.logistics.domain.port.HubConnectionRepository;
import br.furb.logistics.domain.port.HubRepository;
import br.furb.logistics.domain.port.InboxRepository;
import br.furb.logistics.domain.port.OutboxRepository;
import br.furb.logistics.domain.port.RouteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Objects.nonNull;
import static org.springframework.util.CollectionUtils.isEmpty;

@Slf4j
@RequiredArgsConstructor
public class CalculateRouteUseCase {

    private final RouteRepository routeRepository;
    private final HubRepository hubRepository;
    private final HubConnectionRepository hubConnectionRepository;
    private final CepLookupPort cepLookupPort;
    private final OutboxRepository outboxRepository;
    private final InboxRepository inboxRepository;
    private final RouteCalculationService routeCalculationService;
    private final TransactionTemplate transactionTemplate;

    public RouteResponse execute(String eventId, String packageId, String senderCep, String recipientCep) {
        if (inboxRepository.existsByEventId(eventId)) {
            log.info("[calculate-route] Event {} already processed, skipping", eventId);
            return null;
        }

        log.info("[calculate-route] Calculating route for package {} from {} to {}", packageId, senderCep, recipientCep);

        // External lookups (ViaCEP) and the routing computation run OUTSIDE the write transaction.
        CepInfo originCepInfo = cepLookupPort.findByCep(senderCep)
                .orElseThrow(() -> new CepValidationException(senderCep));

        CepInfo destinationCepInfo = cepLookupPort.findByCep(recipientCep)
                .orElseThrow(() -> new CepValidationException(recipientCep));

        List<Hub> allHubs = hubRepository.findAllActive();
        List<HubConnection> allConnections = hubConnectionRepository.findAll();
        List<Hub> originHubs = getActiveCandidates(originCepInfo.getCity(), originCepInfo.getState(), allHubs);
        List<Hub> destinationHubs = getActiveCandidates(destinationCepInfo.getCity(), destinationCepInfo.getState(), allHubs);

        RouteCalculationService.SelectedRoute selectedRoute = routeCalculationService.selectBestRoute(
                originHubs,
                destinationHubs,
                allHubs,
                allConnections
        );
        Hub originHub = selectedRoute.originHub();
        Hub destinationHub = selectedRoute.destinationHub();
        RouteCalculationService.RouteResult result = selectedRoute.routeResult();

        Map<String, Hub> hubMap = allHubs.stream().collect(Collectors.toMap(Hub::getId, h -> h));
        List<Route.RouteHop> hops = buildHops(result.path(), hubMap);

        Route existingRoute = routeRepository.findByPackageId(packageId).orElse(null);
        Route route = buildRoute(existingRoute, packageId, originHub.getId(), destinationHub.getId(), hops, result);

        // Narrow transaction: claim the inbox key, persist the route and the outbox event atomically.
        return transactionTemplate.execute(status -> {
            if (!inboxRepository.saveIfAbsent(eventId, "package.created")) {
                log.info("[calculate-route] Event {} already processed, skipping", eventId);
                return null;
            }

            Route saved = routeRepository.save(route);
            outboxRepository.save(buildCalculatedEvent(saved, packageId, originHub, destinationHub, hops, result));

            log.info("[calculate-route] Route {} calculated for package {}", saved.getId(), packageId);
            return RouteMapper.INSTANCE.toResponse(saved);
        });
    }

    private List<Route.RouteHop> buildHops(List<String> path, Map<String, Hub> hubMap) {
        List<Route.RouteHop> hops = new ArrayList<>();
        for (int i = 0; i < path.size(); i++) {
            Hub hub = hubMap.get(path.get(i));
            hops.add(Route.RouteHop.builder()
                    .hubId(hub.getId())
                    .hubName(hub.getName())
                    .order(i + 1)
                    .build());
        }
        return hops;
    }

    private RouteCalculatedEvent buildCalculatedEvent(Route saved,
                                                      String packageId,
                                                      Hub originHub,
                                                      Hub destinationHub,
                                                      List<Route.RouteHop> hops,
                                                      RouteCalculationService.RouteResult result) {
        List<RouteCalculatedEvent.HopInfo> eventHops = hops.stream()
                .map(hop -> RouteCalculatedEvent.HopInfo.builder()
                        .hubId(hop.getHubId())
                        .name(hop.getHubName())
                        .order(hop.getOrder())
                        .build())
                .toList();

        return RouteCalculatedEvent.builder()
                .payload(RouteCalculatedEvent.Payload.builder()
                        .packageId(packageId)
                        .routeId(saved.getId())
                        .originHub(RouteCalculatedEvent.HubInfo.builder()
                                .id(originHub.getId())
                                .name(originHub.getName())
                                .city(originHub.getCity())
                                .state(originHub.getState())
                                .build())
                        .destinationHub(RouteCalculatedEvent.HubInfo.builder()
                                .id(destinationHub.getId())
                                .name(destinationHub.getName())
                                .city(destinationHub.getCity())
                                .state(destinationHub.getState())
                                .build())
                        .hops(eventHops)
                        .totalDistanceKm(result.totalDistanceKm())
                        .estimatedTransitHours(result.totalTransitHours())
                        .build())
                .build();
    }

        private Route buildRoute(Route existingRoute,
                                     String packageId,
                                     String originHubId,
                                     String destinationHubId,
                                     List<Route.RouteHop> hops,
                                     RouteCalculationService.RouteResult result) {
                Instant now = Instant.now();

                return Route.builder()
                                .id(nonNull(existingRoute) ? existingRoute.getId() : null)
                                .packageId(packageId)
                                .originHubId(originHubId)
                                .destinationHubId(destinationHubId)
                                .hops(hops)
                                .totalDistanceKm(result.totalDistanceKm())
                                .estimatedTransitHours(result.totalTransitHours())
                                .status(RouteStatus.CALCULATED)
                                .createdAt(nonNull(existingRoute) ? existingRoute.getCreatedAt() : now)
                                .updatedAt(nonNull(existingRoute) ? now : null)
                                .build();
        }

        private List<Hub> getActiveCandidates(String city, String state, List<Hub> activeHubs) {
                // Prefer active hubs in the same city+state.
                List<Hub> cityCandidates = activeHubs.stream()
                                .filter(hub -> state.equalsIgnoreCase(hub.getState()) && city.equalsIgnoreCase(hub.getCity()))
                                .toList();
                if (!isEmpty(cityCandidates)) {
                        return cityCandidates;
                }

                // Fall back to active hubs in the same state (regional) instead of every hub in the network.
                // A precise nearest-hub (haversine) fallback would require geocoding the CEP, which ViaCEP does not provide.
                // When no same-state hub exists the candidate list is empty and route selection fails explicitly,
                // rather than silently routing the package through arbitrary, unrelated hubs.
                return activeHubs.stream()
                                .filter(hub -> state.equalsIgnoreCase(hub.getState()))
                                .toList();
        }
}
