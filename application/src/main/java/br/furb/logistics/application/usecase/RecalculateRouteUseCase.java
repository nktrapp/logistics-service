package br.furb.logistics.application.usecase;

import br.furb.logistics.application.service.RouteCalculationService;
import br.furb.logistics.application.usecase.transaction.PersistRecalculatedRouteUseCase;
import br.furb.logistics.domain.exception.CepValidationException;
import br.furb.logistics.domain.model.CepInfo;
import br.furb.logistics.domain.model.Hub;
import br.furb.logistics.domain.model.HubConnection;
import br.furb.logistics.domain.model.Route;
import br.furb.logistics.domain.model.RouteStatus;
import br.furb.logistics.domain.port.CepLookupPort;
import br.furb.logistics.domain.port.HubConnectionRepositoryPort;
import br.furb.logistics.domain.port.HubRepositoryPort;
import br.furb.logistics.domain.port.InboxRepositoryPort;
import br.furb.logistics.domain.port.RouteRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Objects.nonNull;
import static org.springframework.util.CollectionUtils.isEmpty;

@Slf4j
@RequiredArgsConstructor
public class RecalculateRouteUseCase {

    private final RouteRepositoryPort routeRepository;
    private final HubRepositoryPort hubRepository;
    private final HubConnectionRepositoryPort hubConnectionRepository;
    private final CepLookupPort cepLookupPort;
    private final InboxRepositoryPort inboxRepository;
    private final RouteCalculationService routeCalculationService;
    private final PersistRecalculatedRouteUseCase persistRecalculatedRouteUseCase;

    public void execute(String eventId, String packageId, String newRecipientCep) {
        if (inboxRepository.existsByEventId(eventId)) {
            log.info("[recalculate-route] Event {} already processed, skipping", eventId);
            return;
        }

        log.info("[recalculate-route] Recalculating route for package {} to new CEP {}", packageId, newRecipientCep);

        // External lookups (ViaCEP) and the routing computation run OUTSIDE the write transaction.
        Route existingRoute = routeRepository.findByPackageId(packageId).orElse(null);

        String originHubId = nonNull(existingRoute)
                ? existingRoute.getOriginHubId()
                : hubRepository.findAllActive().getFirst().getId();

        CepInfo destinationCepInfo = cepLookupPort.findByCep(newRecipientCep)
                .orElseThrow(() -> new CepValidationException(newRecipientCep));

        List<Hub> allHubs = hubRepository.findAllActive();
        List<HubConnection> allConnections = hubConnectionRepository.findAll();
        Hub originHub = resolveOriginHub(originHubId, allHubs);
        List<Hub> routingHubs = includeOriginHub(allHubs, originHub);
        List<Hub> destinationHubs = getActiveCandidates(destinationCepInfo.getCity(), destinationCepInfo.getState(), allHubs);

        RouteCalculationService.SelectedRoute selectedRoute = routeCalculationService.selectBestRoute(
                List.of(originHub),
                destinationHubs,
                routingHubs,
                allConnections
        );
        Hub destinationHub = selectedRoute.destinationHub();
        RouteCalculationService.RouteResult result = selectedRoute.routeResult();

        Map<String, Hub> hubMap = routingHubs.stream().collect(Collectors.toMap(Hub::getId, h -> h));
        List<Route.RouteHop> hops = buildHops(result.path(), hubMap);

        Route route = buildRoute(existingRoute, packageId, originHubId, destinationHub.getId(), hops, result);

        persistRecalculatedRouteUseCase.execute(
                eventId,
                packageId,
                route,
                originHub,
                destinationHub,
                hops,
                result
        );
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

        private Hub resolveOriginHub(String originHubId, List<Hub> activeHubs) {
                return activeHubs.stream()
                                .filter(hub -> hub.getId().equals(originHubId))
                                .findFirst()
                                .orElseGet(() -> hubRepository.findById(originHubId)
                                                .orElseThrow(() -> new br.furb.logistics.domain.exception.RouteCalculationException(
                                                                "Origin hub not found for route recalculation: " + originHubId
                                                )));
        }

        private List<Hub> includeOriginHub(List<Hub> activeHubs, Hub originHub) {
                if (activeHubs.stream().anyMatch(hub -> hub.getId().equals(originHub.getId()))) {
                        return activeHubs;
                }

                List<Hub> routingHubs = new ArrayList<>(activeHubs);
                routingHubs.add(originHub);
                return routingHubs;
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
                // When no same-state hub exists the candidate list is empty and route selection fails explicitly,
                // rather than silently routing the package through arbitrary, unrelated hubs.
                return activeHubs.stream()
                                .filter(hub -> state.equalsIgnoreCase(hub.getState()))
                                .toList();
        }
}
