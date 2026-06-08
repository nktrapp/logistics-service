package br.furb.logistics.application.usecase.transaction;

import br.furb.logistics.domain.event.DomainEvent;
import br.furb.logistics.domain.event.HubConnectionsCreatedEvent;
import br.furb.logistics.domain.model.HubConnection;
import br.furb.logistics.domain.port.HubConnectionRepositoryPort;
import br.furb.logistics.domain.port.InboxRepositoryPort;
import br.furb.logistics.domain.port.OutboxRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PersistHubConnectionsUseCase")
class PersistHubConnectionsUseCaseTest {

    @Mock
    HubConnectionRepositoryPort hubConnectionRepository;

    @Mock
    OutboxRepositoryPort outboxRepository;

    @Mock
    InboxRepositoryPort inboxRepository;

    @Test
    @DisplayName("Given a not-yet-claimed event, should claim the inbox, save the connections and enqueue hub.connections.created")
    void shouldPersistConnectionsAndEnqueueEvent() {
        PersistHubConnectionsUseCase useCase = new PersistHubConnectionsUseCase(hubConnectionRepository, outboxRepository, inboxRepository);
        when(inboxRepository.saveIfAbsent("event-1", "hub.created")).thenReturn(true);
        when(hubConnectionRepository.save(any(HubConnection.class))).thenAnswer(invocation -> invocation.getArgument(0));
        List<HubConnection> connections = List.of(
                connection("blu", "joi"),
                connection("blu", "fln"));

        List<HubConnection> saved = useCase.execute("event-1", "blu", connections);

        assertThat(saved).hasSize(2);
        verify(hubConnectionRepository, org.mockito.Mockito.times(2)).save(any(HubConnection.class));

        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(outboxRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(HubConnectionsCreatedEvent.class);
        HubConnectionsCreatedEvent event = (HubConnectionsCreatedEvent) eventCaptor.getValue();
        assertThat(event.getPayload().getHubId()).isEqualTo("blu");
        assertThat(event.getPayload().getNeighborHubIds()).containsExactly("joi", "fln");
    }

    @Test
    @DisplayName("Given an already claimed event, should be idempotent and not save connections or enqueue an event")
    void shouldSkipWhenEventAlreadyClaimed() {
        PersistHubConnectionsUseCase useCase = new PersistHubConnectionsUseCase(hubConnectionRepository, outboxRepository, inboxRepository);
        when(inboxRepository.saveIfAbsent("event-1", "hub.created")).thenReturn(false);

        List<HubConnection> saved = useCase.execute("event-1", "blu", List.of(connection("blu", "joi")));

        assertThat(saved).isEmpty();
        verify(hubConnectionRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    @DisplayName("Given no connections to create, should claim the inbox but neither save nor enqueue an event")
    void shouldClaimInboxButSkipPersistenceWhenNoConnections() {
        PersistHubConnectionsUseCase useCase = new PersistHubConnectionsUseCase(hubConnectionRepository, outboxRepository, inboxRepository);
        when(inboxRepository.saveIfAbsent("event-1", "hub.created")).thenReturn(true);

        List<HubConnection> saved = useCase.execute("event-1", "blu", List.of());

        assertThat(saved).isEmpty();
        verify(hubConnectionRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    private HubConnection connection(String originHubId, String destinationHubId) {
        return HubConnection.builder()
                .originHubId(originHubId)
                .destinationHubId(destinationHubId)
                .distanceKm(BigDecimal.valueOf(70))
                .transitTimeHours(2)
                .build();
    }
}
