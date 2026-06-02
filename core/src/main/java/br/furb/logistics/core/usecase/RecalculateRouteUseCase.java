package br.furb.logistics.core.usecase;

import br.furb.logistics.core.service.RouteCalculationService;
import br.furb.logistics.domain.event.RouteCalculatedEvent;
import br.furb.logistics.domain.event.RouteRecalculatedEvent;
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
import org.springframework.transaction.annotation.Transactional;

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

    private final RouteRepository routeRepository;
    private final HubRepository hubRepository;
    private final HubConnectionRepository hubConnectionRepository;
    private final CepLookupPort cepLookupPort;
    private final OutboxRepository outboxRepository;
    private final InboxRepository inboxRepository;
    private final RouteCalculationService routeCalculationService;

    @Transactional
    public void execute(String eventId, String packageId, String newRecipientCep) {
                if (!inboxRepository.saveIfAbsent(eventId, "package.destination.changed")) {
            log.info("[recalculate-route] Event {} already processed, skipping", eventId);
            return;
        }

        log.info("[recalculate-route] Recalculating route for package {} to new CEP {}", packageId, newRecipientCep);

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

        List<Route.RouteHop> hops = new ArrayList<>();
        List<String> path = result.path();
        for (int i = 0; i < path.size(); i++) {
            Hub hub = hubMap.get(path.get(i));
            hops.add(Route.RouteHop.builder()
                    .hubId(hub.getId())
                    .hubName(hub.getName())
                    .order(i + 1)
                    .build());
        }

        Route route = buildRoute(existingRoute, packageId, originHubId, destinationHub.getId(), hops, result);

        Route saved = routeRepository.save(route);

        List<RouteCalculatedEvent.HopInfo> eventHops = hops.stream()
                .map(hop -> RouteCalculatedEvent.HopInfo.builder()
                        .hubId(hop.getHubId())
                        .name(hop.getHubName())
                        .order(hop.getOrder())
                        .build())
                .toList();

        RouteRecalculatedEvent event = RouteRecalculatedEvent.builder()
                .payload(RouteRecalculatedEvent.Payload.builder()
                        .packageId(packageId)
                        .routeId(saved.getId())
                        .reason("destination.changed")
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

        outboxRepository.save(event);

        log.info("[recalculate-route] Route {} recalculated for package {}", saved.getId(), packageId);
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
                List<Hub> cityCandidates = hubRepository.findByCityAndState(city, state).stream()
                                .filter(candidate -> activeHubs.stream().anyMatch(activeHub -> activeHub.getId().equals(candidate.getId())))
                                .toList();

                if (isEmpty(cityCandidates)) {
                        return activeHubs;
                }

                return cityCandidates;
        }
}
