package br.furb.logistics.application.usecase;

import br.furb.logistics.application.dto.HubResponse;
import br.furb.logistics.domain.model.Hub;
import br.furb.logistics.domain.port.HubRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ListHubsUseCase")
class ListHubsUseCaseTest {

    @Mock
    HubRepositoryPort hubRepository;

    @Test
    @DisplayName("Given several hubs, should map all of them to responses")
    void shouldListHubs() {
        ListHubsUseCase useCase = new ListHubsUseCase(hubRepository);
        when(hubRepository.findAll()).thenReturn(List.of(buildHub("hub-1"), buildHub("hub-2")));

        List<HubResponse> responses = useCase.execute();

        assertThat(responses).hasSize(2);
    }

    @Test
    @DisplayName("Given no hubs, should return an empty list")
    void shouldReturnEmptyWhenNoHubs() {
        ListHubsUseCase useCase = new ListHubsUseCase(hubRepository);
        when(hubRepository.findAll()).thenReturn(List.of());

        List<HubResponse> responses = useCase.execute();

        assertThat(responses).isEmpty();
    }

    private Hub buildHub(String id) {
        return Hub.builder()
                .id(id)
                .name("Hub " + id)
                .cep("89000000")
                .city("Blumenau")
                .state("SC")
                .active(true)
                .build();
    }
}
