package br.furb.logistics.application.usecase.transaction;

import br.furb.logistics.application.service.RouteCalculationService;
import br.furb.logistics.domain.event.RouteCalculatedEvent;
import br.furb.logistics.domain.model.Hub;
import br.furb.logistics.domain.model.Route;
import br.furb.logistics.domain.port.InboxRepositoryPort;
import br.furb.logistics.domain.port.OutboxRepositoryPort;
import br.furb.logistics.domain.port.RouteRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class PersistCalculatedRouteUseCase {

    private final RouteRepositoryPort routeRepository;
    private final OutboxRepositoryPort outboxRepository;
    private final InboxRepositoryPort inboxRepository;

    @Transactional
    public Route execute(String eventId,
                         String packageId,
                         String destinationCep,
                         Route route,
                         Hub originHub,
                         Hub destinationHub,
                         List<Route.RouteHop> hops,
                         RouteCalculationService.RouteResult result) {
        if (!inboxRepository.saveIfAbsent(eventId, "package.created")) {
            log.info("[calculate-route] Event {} already processed, skipping", eventId);
            return null;
        }

        Route saved = routeRepository.save(route);
        outboxRepository.save(buildCalculatedEvent(saved, packageId, destinationCep, originHub, destinationHub, hops, result));

        log.info("[calculate-route] Route {} calculated for package {}", saved.getId(), packageId);
        return saved;
    }

    private RouteCalculatedEvent buildCalculatedEvent(Route saved,
                                                      String packageId,
                                                      String destinationCep,
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
                        .destinationCep(destinationCep)
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
}
