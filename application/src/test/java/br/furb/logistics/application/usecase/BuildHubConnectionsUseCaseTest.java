package br.furb.logistics.application.usecase;

import br.furb.logistics.application.service.HubMeshService;
import br.furb.logistics.application.usecase.transaction.PersistHubConnectionsUseCase;
import br.furb.logistics.domain.model.Hub;
import br.furb.logistics.domain.model.HubConnection;
import br.furb.logistics.domain.port.HubConnectionRepositoryPort;
import br.furb.logistics.domain.port.HubRepositoryPort;
import br.furb.logistics.domain.port.InboxRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BuildHubConnectionsUseCase")
class BuildHubConnectionsUseCaseTest {

    @Mock
    HubRepositoryPort hubRepository;
    @Mock
    HubConnectionRepositoryPort hubConnectionRepository;
    @Mock
    InboxRepositoryPort inboxRepository;
    @Mock
    HubMeshService hubMeshService;
    @Mock
    PersistHubConnectionsUseCase persistHubConnectionsUseCase;

    BuildHubConnectionsUseCase useCase() {
        return new BuildHubConnectionsUseCase(hubRepository, hubConnectionRepository, inboxRepository,
                hubMeshService, persistHubConnectionsUseCase, 3, 60);
    }

    @Test
    @DisplayName("Given an already processed event, should skip without touching the mesh or persistence")
    void shouldSkipWhenEventAlreadyProcessed() {
        when(inboxRepository.existsByEventId("event-1")).thenReturn(true);

        useCase().execute("event-1", "hub-1");

        verify(hubRepository, never()).findById(any());
        verifyNoInteractions(hubMeshService, persistHubConnectionsUseCase);
    }

    @Test
    @DisplayName("Given a missing hub, should skip mesh generation")
    void shouldSkipWhenHubNotFound() {
        when(inboxRepository.existsByEventId("event-1")).thenReturn(false);
        when(hubRepository.findById("hub-1")).thenReturn(Optional.empty());

        useCase().execute("event-1", "hub-1");

        verifyNoInteractions(hubMeshService, persistHubConnectionsUseCase);
    }

    @Test
    @DisplayName("Given a resolvable hub, should compute neighbours and delegate persistence")
    void shouldComputeAndPersistConnections() {
        Hub hub = Hub.builder().id("hub-1").name("Hub").cep("89000000").city("Blumenau").state("SC")
                .latitude(-26.92).longitude(-49.07).active(true).build();
        List<Hub> activeHubs = List.of(hub);
        List<HubConnection> existing = List.of();
        List<HubConnection> computed = List.of(HubConnection.builder()
                .originHubId("hub-1").destinationHubId("hub-2")
                .distanceKm(BigDecimal.valueOf(70)).transitTimeHours(2).build());

        when(inboxRepository.existsByEventId("event-1")).thenReturn(false);
        when(hubRepository.findById("hub-1")).thenReturn(Optional.of(hub));
        when(hubRepository.findAllActive()).thenReturn(activeHubs);
        when(hubConnectionRepository.findAll()).thenReturn(existing);
        when(hubMeshService.computeConnections(eq(hub), eq(activeHubs), eq(existing), anyInt(), anyInt()))
                .thenReturn(computed);

        useCase().execute("event-1", "hub-1");

        verify(persistHubConnectionsUseCase).execute("event-1", "hub-1", computed);
    }
}
