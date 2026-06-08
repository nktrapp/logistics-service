package br.furb.logistics.application.usecase.transaction;

import br.furb.logistics.application.service.RouteCalculationService;
import br.furb.logistics.domain.event.DomainEvent;
import br.furb.logistics.domain.event.RouteCalculatedEvent;
import br.furb.logistics.domain.model.Hub;
import br.furb.logistics.domain.model.Route;
import br.furb.logistics.domain.model.RouteStatus;
import br.furb.logistics.domain.port.InboxRepositoryPort;
import br.furb.logistics.domain.port.OutboxRepositoryPort;
import br.furb.logistics.domain.port.RouteRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PersistCalculatedRouteUseCase")
class PersistCalculatedRouteUseCaseTest {

    @Mock
    RouteRepositoryPort routeRepository;

    @Mock
    OutboxRepositoryPort outboxRepository;

    @Mock
    InboxRepositoryPort inboxRepository;

    @Test
    @DisplayName("Given a not-yet-claimed event, should claim the inbox, save the route and enqueue route.calculated")
    void shouldPersistRouteAndEnqueueEvent() {
        PersistCalculatedRouteUseCase useCase = new PersistCalculatedRouteUseCase(routeRepository, outboxRepository, inboxRepository);
        Route route = buildRoute();
        Hub originHub = buildHub("origin-1", "Origin Hub", "Blumenau", "SC");
        Hub destinationHub = buildHub("dest-1", "Destination Hub", "Joinville", "SC");
        List<Route.RouteHop> hops = List.of(Route.RouteHop.builder().hubId("origin-1").hubName("Origin Hub").order(1).build());
        RouteCalculationService.RouteResult result = new RouteCalculationService.RouteResult(List.of("origin-1", "dest-1"), 50.0, 6);

        when(inboxRepository.saveIfAbsent("event-1", "package.created")).thenReturn(true);
        when(routeRepository.save(route)).thenReturn(route);

        Route saved = useCase.execute("event-1", "pkg-1", route, originHub, destinationHub, hops, result);

        assertThat(saved).isEqualTo(route);
        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(outboxRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(RouteCalculatedEvent.class);
        RouteCalculatedEvent event = (RouteCalculatedEvent) eventCaptor.getValue();
        assertThat(event.getPayload().getPackageId()).isEqualTo("pkg-1");
        assertThat(event.getPayload().getRouteId()).isEqualTo("route-1");
        assertThat(event.getPayload().getTotalDistanceKm()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("Given an already claimed event, should be idempotent and not save the route or enqueue an event")
    void shouldSkipWhenEventAlreadyClaimed() {
        PersistCalculatedRouteUseCase useCase = new PersistCalculatedRouteUseCase(routeRepository, outboxRepository, inboxRepository);
        when(inboxRepository.saveIfAbsent("event-1", "package.created")).thenReturn(false);

        Route saved = useCase.execute("event-1", "pkg-1", buildRoute(),
                buildHub("origin-1", "Origin", "Blumenau", "SC"),
                buildHub("dest-1", "Dest", "Joinville", "SC"),
                List.of(), new RouteCalculationService.RouteResult(List.of(), 0.0, 0));

        assertThat(saved).isNull();
        verify(routeRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    private Route buildRoute() {
        return Route.builder()
                .id("route-1")
                .packageId("pkg-1")
                .originHubId("origin-1")
                .destinationHubId("dest-1")
                .hops(List.of())
                .totalDistanceKm(50.0)
                .estimatedTransitHours(6)
                .status(RouteStatus.CALCULATED)
                .createdAt(Instant.parse("2026-05-31T10:00:00Z"))
                .build();
    }

    private Hub buildHub(String id, String name, String city, String state) {
        return Hub.builder()
                .id(id)
                .name(name)
                .cep("89000000")
                .city(city)
                .state(state)
                .active(true)
                .build();
    }
}
