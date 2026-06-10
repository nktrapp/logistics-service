package br.furb.logistics.application.usecase;

import br.furb.logistics.application.dto.RouteResponse;
import br.furb.logistics.application.mapper.RouteMapper;
import br.furb.logistics.application.service.RouteCalculationService;
import br.furb.logistics.application.usecase.transaction.PersistCalculatedRouteUseCase;
import br.furb.logistics.application.usecase.transaction.PersistFailedRouteUseCase;
import br.furb.logistics.domain.exception.RouteCalculationException;
import br.furb.logistics.domain.model.CepInfo;
import br.furb.logistics.domain.model.Hub;
import br.furb.logistics.domain.model.Route;
import br.furb.logistics.domain.model.RouteStatus;
import br.furb.logistics.domain.port.CepLookupPort;
import br.furb.logistics.domain.port.HubConnectionRepositoryPort;
import br.furb.logistics.domain.port.HubRepositoryPort;
import br.furb.logistics.domain.port.InboxRepositoryPort;
import br.furb.logistics.domain.port.RouteRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CalculateRouteUseCase")
class CalculateRouteUseCaseTest {

    @Mock
    RouteRepositoryPort routeRepository;

    @Mock
    HubRepositoryPort hubRepository;

    @Mock
    HubConnectionRepositoryPort hubConnectionRepository;

    @Mock
    CepLookupPort cepLookupPort;

    @Mock
    InboxRepositoryPort inboxRepository;

    @Mock
    RouteCalculationService routeCalculationService;

    @Mock
    PersistCalculatedRouteUseCase persistCalculatedRouteUseCase;

    @Mock
    PersistFailedRouteUseCase persistFailedRouteUseCase;

    private final RouteMapper routeMapper = Mappers.getMapper(RouteMapper.class);

    private CalculateRouteUseCase buildUseCase() {
        return new CalculateRouteUseCase(
                routeRepository,
                hubRepository,
                hubConnectionRepository,
                cepLookupPort,
                inboxRepository,
                routeCalculationService,
                persistCalculatedRouteUseCase,
                persistFailedRouteUseCase,
                routeMapper
        );
    }

    @Test
    @DisplayName("Given an already processed event, should skip without any lookup or persistence")
    void shouldSkipWhenEventAlreadyProcessed() {
        CalculateRouteUseCase useCase = buildUseCase();
        when(inboxRepository.existsByEventId("event-1")).thenReturn(true);

        RouteResponse response = useCase.execute("event-1", "pkg-1", "89010000", "89200000");

        assertThat(response).isNull();
        verifyNoInteractions(cepLookupPort, routeCalculationService, persistCalculatedRouteUseCase, persistFailedRouteUseCase);
    }

    @Test
    @DisplayName("Given resolvable CEPs and a reachable route, should persist the calculated route as CALCULATED")
    void shouldCalculateAndPersistRoute() {
        CalculateRouteUseCase useCase = buildUseCase();
        Hub originHub = buildHub("origin-1", "Origin Hub", "Blumenau", "SC");
        Hub destinationHub = buildHub("dest-1", "Destination Hub", "Joinville", "SC");
        RouteCalculationService.RouteResult result = new RouteCalculationService.RouteResult(
                List.of("origin-1", "dest-1"), 50.0, 6);

        when(inboxRepository.existsByEventId("event-1")).thenReturn(false);
        when(cepLookupPort.findByCep("89010000")).thenReturn(Optional.of(buildCep("89010000", "Blumenau", "SC")));
        when(cepLookupPort.findByCep("89200000")).thenReturn(Optional.of(buildCep("89200000", "Joinville", "SC")));
        when(hubRepository.findAllActive()).thenReturn(List.of(originHub, destinationHub));
        when(hubConnectionRepository.findAll()).thenReturn(List.of());
        when(routeCalculationService.selectBestRoute(any(), any(), any(), any()))
                .thenReturn(new RouteCalculationService.SelectedRoute(originHub, destinationHub, result));
        when(routeRepository.findByPackageId("pkg-1")).thenReturn(Optional.empty());

        Route saved = Route.builder()
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
        when(persistCalculatedRouteUseCase.execute(any(), any(), any(), any(Route.class), any(Hub.class), any(Hub.class), any(), any()))
                .thenReturn(saved);

        RouteResponse response = useCase.execute("event-1", "pkg-1", "89010000", "89200000");

        assertThat(response).isNotNull();
        ArgumentCaptor<Route> routeCaptor = ArgumentCaptor.forClass(Route.class);
        verify(persistCalculatedRouteUseCase).execute(
                eq("event-1"), eq("pkg-1"), eq("89200000"), routeCaptor.capture(), any(Hub.class), any(Hub.class), any(), eq(result));
        Route built = routeCaptor.getValue();
        assertThat(built.getStatus()).isEqualTo(RouteStatus.CALCULATED);
        assertThat(built.getPackageId()).isEqualTo("pkg-1");
        assertThat(built.getOriginHubId()).isEqualTo("origin-1");
        assertThat(built.getDestinationHubId()).isEqualTo("dest-1");
        assertThat(built.getTotalDistanceKm()).isEqualTo(50.0);
        verify(persistFailedRouteUseCase, never()).execute(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Given an unresolvable origin CEP, should emit route.failed and not persist a calculated route")
    void shouldEmitFailedWhenCepInvalid() {
        CalculateRouteUseCase useCase = buildUseCase();
        when(inboxRepository.existsByEventId("event-1")).thenReturn(false);
        when(cepLookupPort.findByCep("00000000")).thenReturn(Optional.empty());

        RouteResponse response = useCase.execute("event-1", "pkg-1", "00000000", "89200000");

        assertThat(response).isNull();
        verify(persistFailedRouteUseCase).execute(eq("event-1"), eq("package.created"), eq("pkg-1"), anyString());
        verify(persistCalculatedRouteUseCase, never())
                .execute(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Given no reachable/eligible hubs, should emit route.failed when the routing service throws")
    void shouldEmitFailedWhenRoutingFails() {
        CalculateRouteUseCase useCase = buildUseCase();
        when(inboxRepository.existsByEventId("event-1")).thenReturn(false);
        when(cepLookupPort.findByCep("89010000")).thenReturn(Optional.of(buildCep("89010000", "Blumenau", "SC")));
        when(cepLookupPort.findByCep("89200000")).thenReturn(Optional.of(buildCep("89200000", "Joinville", "SC")));
        when(hubRepository.findAllActive()).thenReturn(List.of(buildHub("origin-1", "Origin", "Blumenau", "SC")));
        when(hubConnectionRepository.findAll()).thenReturn(List.of());
        when(routeCalculationService.selectBestRoute(any(), any(), any(), any()))
                .thenThrow(new RouteCalculationException("no reachable hub"));

        RouteResponse response = useCase.execute("event-1", "pkg-1", "89010000", "89200000");

        assertThat(response).isNull();
        verify(persistFailedRouteUseCase).execute(eq("event-1"), eq("package.created"), eq("pkg-1"), anyString());
        verify(persistCalculatedRouteUseCase, never())
                .execute(any(), any(), any(), any(), any(), any(), any(), any());
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

    private CepInfo buildCep(String cep, String city, String state) {
        return CepInfo.builder()
                .cep(cep)
                .city(city)
                .state(state)
                .neighborhood("Centro")
                .build();
    }
}
