package br.furb.logistics.application.usecase;

import br.furb.logistics.application.dto.RegisterConnectionCommand;
import br.furb.logistics.application.mapper.HubConnectionMapper;
import br.furb.logistics.domain.exception.HubNotFoundException;
import br.furb.logistics.domain.model.Hub;
import br.furb.logistics.domain.model.HubConnection;
import br.furb.logistics.domain.port.HubConnectionRepositoryPort;
import br.furb.logistics.domain.port.HubRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RegisterHubConnectionUseCase")
class RegisterHubConnectionUseCaseTest {

    @Mock
    HubConnectionRepositoryPort hubConnectionRepository;

    @Mock
    HubRepositoryPort hubRepository;

    private final HubConnectionMapper hubConnectionMapper = Mappers.getMapper(HubConnectionMapper.class);

    private RegisterConnectionCommand command() {
        return new RegisterConnectionCommand("origin-1", "dest-1", new BigDecimal("50.0"), 6);
    }

    @Test
    @DisplayName("Given both hubs exist, should persist the connection")
    void shouldRegisterConnection() {
        RegisterHubConnectionUseCase useCase = new RegisterHubConnectionUseCase(hubConnectionRepository, hubRepository, hubConnectionMapper);
        when(hubRepository.findById("origin-1")).thenReturn(Optional.of(buildHub("origin-1")));
        when(hubRepository.findById("dest-1")).thenReturn(Optional.of(buildHub("dest-1")));
        HubConnection saved = HubConnection.builder()
                .id("conn-1").originHubId("origin-1").destinationHubId("dest-1")
                .distanceKm(new BigDecimal("50.0")).transitTimeHours(6).build();
        when(hubConnectionRepository.save(any(HubConnection.class))).thenReturn(saved);

        HubConnection result = useCase.execute(command());

        assertThat(result).isEqualTo(saved);
        verify(hubConnectionRepository).save(any(HubConnection.class));
    }

    @Test
    @DisplayName("Given the origin hub does not exist, should throw HubNotFoundException and not persist")
    void shouldThrowWhenOriginMissing() {
        RegisterHubConnectionUseCase useCase = new RegisterHubConnectionUseCase(hubConnectionRepository, hubRepository, hubConnectionMapper);
        when(hubRepository.findById("origin-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(command()))
                .isInstanceOf(HubNotFoundException.class);

        verify(hubConnectionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Given the destination hub does not exist, should throw HubNotFoundException and not persist")
    void shouldThrowWhenDestinationMissing() {
        RegisterHubConnectionUseCase useCase = new RegisterHubConnectionUseCase(hubConnectionRepository, hubRepository, hubConnectionMapper);
        when(hubRepository.findById("origin-1")).thenReturn(Optional.of(buildHub("origin-1")));
        when(hubRepository.findById("dest-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(command()))
                .isInstanceOf(HubNotFoundException.class);

        verify(hubConnectionRepository, never()).save(any());
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
