package br.furb.logistics.application.usecase.transaction;

import br.furb.logistics.domain.event.DomainEvent;
import br.furb.logistics.domain.event.RouteFailedEvent;
import br.furb.logistics.domain.port.InboxRepositoryPort;
import br.furb.logistics.domain.port.OutboxRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PersistFailedRouteUseCase")
class PersistFailedRouteUseCaseTest {

    @Mock
    OutboxRepositoryPort outboxRepository;

    @Mock
    InboxRepositoryPort inboxRepository;

    @Test
    @DisplayName("Given a not-yet-claimed event, should claim the inbox and enqueue route.failed with the reason")
    void shouldEnqueueFailedEvent() {
        PersistFailedRouteUseCase useCase = new PersistFailedRouteUseCase(outboxRepository, inboxRepository);
        when(inboxRepository.saveIfAbsent("event-1", "package.created")).thenReturn(true);

        useCase.execute("event-1", "package.created", "pkg-1", "no reachable hub");

        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(outboxRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(RouteFailedEvent.class);
        RouteFailedEvent event = (RouteFailedEvent) eventCaptor.getValue();
        assertThat(event.getPayload().getPackageId()).isEqualTo("pkg-1");
        assertThat(event.getPayload().getReason()).isEqualTo("no reachable hub");
        assertThat(event.getPartitionKey()).isEqualTo("pkg-1");
    }

    @Test
    @DisplayName("Given an already claimed event, should be idempotent and not enqueue an event")
    void shouldSkipWhenEventAlreadyClaimed() {
        PersistFailedRouteUseCase useCase = new PersistFailedRouteUseCase(outboxRepository, inboxRepository);
        when(inboxRepository.saveIfAbsent("event-1", "package.created")).thenReturn(false);

        useCase.execute("event-1", "package.created", "pkg-1", "reason");

        verify(outboxRepository, never()).save(any());
    }
}
