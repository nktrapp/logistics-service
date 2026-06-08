package br.furb.logistics.application.usecase;

import br.furb.logistics.application.dto.HubResponse;
import br.furb.logistics.application.dto.RegisterHubCommand;
import br.furb.logistics.domain.exception.CepValidationException;
import br.furb.logistics.domain.model.CepInfo;
import br.furb.logistics.domain.model.Hub;
import br.furb.logistics.domain.port.CepLookupPort;
import br.furb.logistics.domain.port.HubRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RegisterHubUseCase")
class RegisterHubUseCaseTest {

    @Mock
    HubRepositoryPort hubRepository;

    @Mock
    CepLookupPort cepLookupPort;

    @Test
    @DisplayName("Given a resolvable CEP, should enrich the hub from ViaCEP data and persist it as active")
    void shouldRegisterHubFromCepData() {
        RegisterHubUseCase useCase = new RegisterHubUseCase(hubRepository, cepLookupPort);
        when(cepLookupPort.findByCep("89010000")).thenReturn(Optional.of(CepInfo.builder()
                .cep("89010000").city("Blumenau").state("SC").neighborhood("Centro").build()));
        when(hubRepository.save(any(Hub.class))).thenAnswer(invocation -> {
            Hub argument = invocation.getArgument(0);
            return argument.toBuilder().id("hub-1").build();
        });

        HubResponse response = useCase.execute(new RegisterHubCommand("Hub Centro", "89010000"));

        ArgumentCaptor<Hub> hubCaptor = ArgumentCaptor.forClass(Hub.class);
        verify(hubRepository).save(hubCaptor.capture());
        Hub persisted = hubCaptor.getValue();
        assertThat(persisted.getName()).isEqualTo("Hub Centro");
        assertThat(persisted.getCity()).isEqualTo("Blumenau");
        assertThat(persisted.getState()).isEqualTo("SC");
        assertThat(persisted.getCep()).isEqualTo("89010000");
        assertThat(persisted.isActive()).isTrue();

        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("Given an unresolvable CEP, should throw CepValidationException and not persist the hub")
    void shouldThrowWhenCepInvalid() {
        RegisterHubUseCase useCase = new RegisterHubUseCase(hubRepository, cepLookupPort);
        when(cepLookupPort.findByCep("00000000")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new RegisterHubCommand("Hub Centro", "00000000")))
                .isInstanceOf(CepValidationException.class);

        verify(hubRepository, never()).save(any());
    }
}
