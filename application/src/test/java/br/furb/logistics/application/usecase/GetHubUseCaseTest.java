package br.furb.logistics.application.usecase;

import br.furb.logistics.application.dto.HubResponse;
import br.furb.logistics.application.mapper.HubMapper;
import br.furb.logistics.domain.exception.HubNotFoundException;
import br.furb.logistics.domain.model.Hub;
import br.furb.logistics.domain.port.HubRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mapstruct.factory.Mappers;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetHubUseCase")
class GetHubUseCaseTest {

    @Mock
    HubRepositoryPort hubRepository;

    private final HubMapper hubMapper = Mappers.getMapper(HubMapper.class);

    @Test
    @DisplayName("Given an existing hub, should return its response")
    void shouldReturnHub() {
        GetHubUseCase useCase = new GetHubUseCase(hubRepository, hubMapper);
        Hub hub = Hub.builder()
                .id("hub-1").name("Hub Centro").cep("89010000").city("Blumenau").state("SC").active(true).build();
        when(hubRepository.findById("hub-1")).thenReturn(Optional.of(hub));

        HubResponse response = useCase.execute("hub-1");

        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("Given an unknown hub, should throw HubNotFoundException")
    void shouldThrowWhenHubNotFound() {
        GetHubUseCase useCase = new GetHubUseCase(hubRepository, hubMapper);
        when(hubRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("missing"))
                .isInstanceOf(HubNotFoundException.class);
    }
}
