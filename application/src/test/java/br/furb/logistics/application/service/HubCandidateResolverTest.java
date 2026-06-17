package br.furb.logistics.application.service;

import br.furb.logistics.domain.model.CepInfo;
import br.furb.logistics.domain.model.Coordinates;
import br.furb.logistics.domain.model.Hub;
import br.furb.logistics.domain.port.MunicipalityGeocodingPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("HubCandidateResolver")
class HubCandidateResolverTest {

    private static final String IBGE_CORNELIO = "4106902";

    @Mock
    MunicipalityGeocodingPort municipalityGeocodingPort;

    @Test
    @DisplayName("Given the nearest hub is in another state, should rank it ahead of a farther same-state hub")
    void shouldRankNearestHubAcrossStateBoundaryFirst() {
        HubCandidateResolver resolver = buildResolver(3);
        Hub ourinhosSP = hub("ourinhos", "Ourinhos SP", "Ourinhos", "SP", -22.97, -49.87);
        Hub curitibaPR = hub("curitiba", "Curitiba PR", "Curitiba", "PR", -25.43, -49.27);
        quandoGeocodificarPorIbge(IBGE_CORNELIO, -23.18, -50.65);

        List<Hub> candidates = resolver.resolve(cepCornelioPR(), List.of(curitibaPR, ourinhosSP));

        assertThat(candidates).containsExactly(ourinhosSP, curitibaPR);
        assertThat(candidates.get(0).getState()).isEqualTo("SP");
    }

    @Test
    @DisplayName("Given more hubs than K, should return only the K nearest ordered by distance")
    void shouldReturnOnlyKNearestOrderedByDistance() {
        HubCandidateResolver resolver = buildResolver(2);
        Hub ourinhosSP = hub("ourinhos", "Ourinhos SP", "Ourinhos", "SP", -22.97, -49.87);
        Hub curitibaPR = hub("curitiba", "Curitiba PR", "Curitiba", "PR", -25.43, -49.27);
        Hub saoPauloSP = hub("saopaulo", "Sao Paulo SP", "Sao Paulo", "SP", -23.55, -46.63);
        quandoGeocodificarPorIbge(IBGE_CORNELIO, -23.18, -50.65);

        List<Hub> candidates = resolver.resolve(cepCornelioPR(), List.of(saoPauloSP, curitibaPR, ourinhosSP));

        assertThat(candidates).containsExactly(ourinhosSP, curitibaPR);
    }

    @Test
    @DisplayName("Given two equidistant hubs, should break the tie deterministically by hub id")
    void shouldBreakDistanceTieByHubId() {
        HubCandidateResolver resolver = buildResolver(2);
        Hub north = hub("z-north", "North", "North City", "NC", 1.0, 0.0);
        Hub south = hub("a-south", "South", "South City", "SC", -1.0, 0.0);
        quandoGeocodificarPorIbge(IBGE_CORNELIO, 0.0, 0.0);

        List<Hub> candidates = resolver.resolve(cepCornelioPR(), List.of(north, south));

        assertThat(candidates).containsExactly(south, north);
    }

    @Test
    @DisplayName("Given the CEP cannot be geocoded, should fall back to same-city candidates")
    void shouldFallBackToCityWhenCepNotGeocoded() {
        HubCandidateResolver resolver = buildResolver(3);
        Hub blumenau = hub("blu", "Blumenau Hub", "Blumenau", "SC", -26.92, -49.07);
        Hub joinville = hub("joi", "Joinville Hub", "Joinville", "SC", -26.30, -48.85);
        when(municipalityGeocodingPort.findByIbgeCode(IBGE_CORNELIO)).thenReturn(Optional.empty());
        when(municipalityGeocodingPort.findByCityState("Blumenau", "SC")).thenReturn(Optional.empty());

        List<Hub> candidates = resolver.resolve(cep("89010000", "Blumenau", "SC"), List.of(blumenau, joinville));

        assertThat(candidates).containsExactly(blumenau);
    }

    @Test
    @DisplayName("Given the CEP geocodes but no active hub has coordinates, should fall back to same-state candidates")
    void shouldFallBackToStateWhenNoHubHasCoordinates() {
        HubCandidateResolver resolver = buildResolver(3);
        Hub joinvilleNoCoords = hub("joi", "Joinville Hub", "Joinville", "SC", 0.0, 0.0);
        quandoGeocodificarPorIbge(IBGE_CORNELIO, -26.92, -49.07);

        List<Hub> candidates = resolver.resolve(cep("89010000", "Blumenau", "SC"), List.of(joinvilleNoCoords));

        assertThat(candidates).containsExactly(joinvilleNoCoords);
    }

    @Test
    @DisplayName("Given neither geocoding nor city/state matches a hub, should return an empty list")
    void shouldReturnEmptyWhenNoCandidateFound() {
        HubCandidateResolver resolver = buildResolver(3);
        Hub saoPaulo = hub("sao", "Sao Paulo Hub", "Sao Paulo", "SP", -23.55, -46.63);
        when(municipalityGeocodingPort.findByIbgeCode(IBGE_CORNELIO)).thenReturn(Optional.empty());
        when(municipalityGeocodingPort.findByCityState("Blumenau", "SC")).thenReturn(Optional.empty());

        List<Hub> candidates = resolver.resolve(cep("89010000", "Blumenau", "SC"), List.of(saoPaulo));

        assertThat(candidates).isEmpty();
    }

    private HubCandidateResolver buildResolver(int maxNearest) {
        return new HubCandidateResolver(municipalityGeocodingPort, maxNearest);
    }

    private void quandoGeocodificarPorIbge(String ibgeCode, double latitude, double longitude) {
        when(municipalityGeocodingPort.findByIbgeCode(ibgeCode))
                .thenReturn(Optional.of(new Coordinates(latitude, longitude)));
    }

    private CepInfo cepCornelioPR() {
        return CepInfo.builder()
                .cep("86300000")
                .city("Cornelio Procopio")
                .state("PR")
                .neighborhood("Centro")
                .ibgeCode(IBGE_CORNELIO)
                .build();
    }

    private CepInfo cep(String cep, String city, String state) {
        return CepInfo.builder()
                .cep(cep)
                .city(city)
                .state(state)
                .neighborhood("Centro")
                .ibgeCode(IBGE_CORNELIO)
                .build();
    }

    private Hub hub(String id, String name, String city, String state, double latitude, double longitude) {
        return Hub.builder()
                .id(id)
                .name(name)
                .cep("00000000")
                .city(city)
                .state(state)
                .latitude(latitude)
                .longitude(longitude)
                .active(true)
                .build();
    }
}
