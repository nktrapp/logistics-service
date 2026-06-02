package br.furb.logistics.core.usecase;

import br.furb.logistics.core.service.RouteCalculationService;
import br.furb.logistics.domain.model.CepInfo;
import br.furb.logistics.domain.model.Hub;
import br.furb.logistics.domain.model.Route;
import br.furb.logistics.domain.model.RouteStatus;
import br.furb.logistics.domain.port.CepLookupPort;
import br.furb.logistics.domain.port.HubConnectionRepository;
import br.furb.logistics.domain.port.HubRepository;
import br.furb.logistics.domain.port.InboxRepository;
import br.furb.logistics.domain.port.OutboxRepository;
import br.furb.logistics.domain.port.RouteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecalculateRouteUseCase")
class RecalculateRouteUseCaseTest {

    @Mock
    RouteRepository routeRepository;

    @Mock
    HubRepository hubRepository;

    @Mock
    HubConnectionRepository hubConnectionRepository;

    @Mock
    CepLookupPort cepLookupPort;

    @Mock
    OutboxRepository outboxRepository;

    @Mock
    InboxRepository inboxRepository;

    @Mock
    RouteCalculationService routeCalculationService;

    @Mock
    TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("Given an existing route, should preserve route identity when recalculating destination")
    void shouldPreserveRouteIdentityWhenRecalculatingDestination() {
        // GIVEN
        RecalculateRouteUseCase useCase = new RecalculateRouteUseCase(
                routeRepository,
                hubRepository,
                hubConnectionRepository,
                cepLookupPort,
                outboxRepository,
                inboxRepository,
                routeCalculationService,
                transactionTemplate
        );
        // Run the transactional callback inline.
        doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        Instant createdAt = Instant.parse("2026-05-31T10:00:00Z");
        Route existingRoute = Route.builder()
                .id("route-1")
                .packageId("pkg-1")
                .originHubId("origin-1")
                .destinationHubId("destination-old")
                .hops(List.of())
                .totalDistanceKm(10.0)
                .estimatedTransitHours(2)
                .status(RouteStatus.CALCULATED)
                .createdAt(createdAt)
                .build();
        Hub originHub = buildHub("origin-1", "Origin Hub", "Blumenau", "SC");
        Hub destinationHub = buildHub("destination-new", "Destination Hub", "Joinville", "SC");
        RouteCalculationService.RouteResult routeResult = new RouteCalculationService.RouteResult(
                List.of("origin-1", "destination-new"),
                35.0,
                5
        );

        when(inboxRepository.saveIfAbsent(eq("event-1"), eq("package.destination.changed"))).thenReturn(true);
        when(routeRepository.findByPackageId("pkg-1")).thenReturn(Optional.of(existingRoute));
        when(cepLookupPort.findByCep("89200000")).thenReturn(Optional.of(CepInfo.builder()
                .cep("89200000")
                .city("Joinville")
                .state("SC")
                .neighborhood("Centro")
                .build()));
        when(hubRepository.findAllActive()).thenReturn(List.of(originHub, destinationHub));
        when(hubConnectionRepository.findAll()).thenReturn(List.of());
        when(routeCalculationService.selectBestRoute(
                eq(List.of(originHub)),
                eq(List.of(destinationHub)),
                eq(List.of(originHub, destinationHub)),
                eq(List.of())
        )).thenReturn(new RouteCalculationService.SelectedRoute(originHub, destinationHub, routeResult));
        when(routeRepository.save(org.mockito.ArgumentMatchers.any(Route.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // WHEN
        useCase.execute("event-1", "pkg-1", "89200000");

        // THEN
        ArgumentCaptor<Route> routeCaptor = ArgumentCaptor.forClass(Route.class);
        verify(routeRepository).save(routeCaptor.capture());
        Route savedRoute = routeCaptor.getValue();
        assertThat(savedRoute.getId()).isEqualTo("route-1");
        assertThat(savedRoute.getCreatedAt()).isEqualTo(createdAt);
        assertThat(savedRoute.getUpdatedAt()).isNotNull();
        assertThat(savedRoute.getDestinationHubId()).isEqualTo("destination-new");
        assertThat(savedRoute.getPackageId()).isEqualTo("pkg-1");
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
