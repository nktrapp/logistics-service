package br.furb.logistics.application.usecase.transaction;

import br.furb.logistics.domain.event.DomainEvent;
import br.furb.logistics.domain.event.HubCreatedEvent;
import br.furb.logistics.domain.model.Hub;
import br.furb.logistics.domain.port.HubRepositoryPort;
import br.furb.logistics.domain.port.OutboxRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PersistRegisteredHubUseCase")
class PersistRegisteredHubUseCaseTest {

    @Mock
    HubRepositoryPort hubRepository;

    @Mock
    OutboxRepositoryPort outboxRepository;

    @Test
    @DisplayName("Should persist the hub and enqueue hub.created carrying the saved hub id")
    void shouldPersistHubAndEnqueueEvent() {
        PersistRegisteredHubUseCase useCase = new PersistRegisteredHubUseCase(hubRepository, outboxRepository);
        when(hubRepository.save(any(Hub.class)))
                .thenAnswer(invocation -> ((Hub) invocation.getArgument(0)).toBuilder().id("hub-1").build());

        Hub saved = useCase.execute(Hub.builder().name("Hub").cep("89010000").city("Blumenau").state("SC").active(true).build());

        assertThat(saved.getId()).isEqualTo("hub-1");
        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(outboxRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(HubCreatedEvent.class);
        HubCreatedEvent event = (HubCreatedEvent) eventCaptor.getValue();
        assertThat(event.getPayload().getHubId()).isEqualTo("hub-1");
        assertThat(event.getPartitionKey()).isEqualTo("hub-1");
    }
}
