package br.furb.logistics.application.usecase;

import br.furb.logistics.application.dto.HubResponse;
import br.furb.logistics.application.dto.RegisterHubCommand;
import br.furb.logistics.application.usecase.transaction.PersistRegisteredHubUseCase;
import br.furb.logistics.domain.exception.CepValidationException;
import br.furb.logistics.domain.model.CepInfo;
import br.furb.logistics.domain.model.Coordinates;
import br.furb.logistics.domain.model.Hub;
import br.furb.logistics.domain.port.CepLookupPort;
import br.furb.logistics.domain.port.MunicipalityGeocodingPort;
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
    CepLookupPort cepLookupPort;

    @Mock
    MunicipalityGeocodingPort municipalityGeocodingPort;

    @Mock
    PersistRegisteredHubUseCase persistRegisteredHubUseCase;

    @Test
    @DisplayName("Given a resolvable CEP with coordinates, should enrich the hub from ViaCEP+IBGE and persist it as active")
    void shouldRegisterHubFromCepAndIbgeData() {
        RegisterHubUseCase useCase = new RegisterHubUseCase(cepLookupPort, municipalityGeocodingPort, persistRegisteredHubUseCase);
        when(cepLookupPort.findByCep("89010000")).thenReturn(Optional.of(CepInfo.builder()
                .cep("89010000").city("Blumenau").state("SC").neighborhood("Centro").ibgeCode("4202404").build()));
        when(municipalityGeocodingPort.findByIbgeCode("4202404"))
                .thenReturn(Optional.of(new Coordinates(-26.9194, -49.0661)));
        when(persistRegisteredHubUseCase.execute(any(Hub.class)))
                .thenAnswer(invocation -> ((Hub) invocation.getArgument(0)).toBuilder().id("hub-1").build());

        HubResponse response = useCase.execute(new RegisterHubCommand("Hub Centro", "89010000"));

        ArgumentCaptor<Hub> hubCaptor = ArgumentCaptor.forClass(Hub.class);
        verify(persistRegisteredHubUseCase).execute(hubCaptor.capture());
        Hub persisted = hubCaptor.getValue();
        assertThat(persisted.getName()).isEqualTo("Hub Centro");
        assertThat(persisted.getCity()).isEqualTo("Blumenau");
        assertThat(persisted.getState()).isEqualTo("SC");
        assertThat(persisted.getCep()).isEqualTo("89010000");
        assertThat(persisted.getLatitude()).isEqualTo(-26.9194);
        assertThat(persisted.getLongitude()).isEqualTo(-49.0661);
        assertThat(persisted.isActive()).isTrue();

        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("Given an unresolvable CEP, should throw CepValidationException and not persist the hub")
    void shouldThrowWhenCepInvalid() {
        RegisterHubUseCase useCase = new RegisterHubUseCase(cepLookupPort, municipalityGeocodingPort, persistRegisteredHubUseCase);
        when(cepLookupPort.findByCep("00000000")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new RegisterHubCommand("Hub Centro", "00000000")))
                .isInstanceOf(CepValidationException.class);

        verify(persistRegisteredHubUseCase, never()).execute(any());
    }

    @Test
    @DisplayName("Given a CEP whose municipality is not geocodable, should still persist the hub without coordinates")
    void shouldRegisterHubWithoutCoordinatesWhenGeocodingFails() {
        RegisterHubUseCase useCase = new RegisterHubUseCase(cepLookupPort, municipalityGeocodingPort, persistRegisteredHubUseCase);
        when(cepLookupPort.findByCep("89010000")).thenReturn(Optional.of(CepInfo.builder()
                .cep("89010000").city("Cidade Desconhecida").state("SC").neighborhood("Centro").ibgeCode("9999999").build()));
        when(municipalityGeocodingPort.findByIbgeCode("9999999")).thenReturn(Optional.empty());
        when(municipalityGeocodingPort.findByCityState("Cidade Desconhecida", "SC")).thenReturn(Optional.empty());
        when(persistRegisteredHubUseCase.execute(any(Hub.class)))
                .thenAnswer(invocation -> ((Hub) invocation.getArgument(0)).toBuilder().id("hub-2").build());

        useCase.execute(new RegisterHubCommand("Hub Desconhecido", "89010000"));

        ArgumentCaptor<Hub> hubCaptor = ArgumentCaptor.forClass(Hub.class);
        verify(persistRegisteredHubUseCase).execute(hubCaptor.capture());
        Hub persisted = hubCaptor.getValue();
        assertThat(persisted.getLatitude()).isZero();
        assertThat(persisted.getLongitude()).isZero();
    }
}
