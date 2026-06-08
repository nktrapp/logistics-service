package br.furb.logistics.infrastructure.adapter.out.integration.geo;

import br.furb.logistics.domain.model.Coordinates;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("IbgeCsvMunicipalityAdapter")
class IbgeCsvMunicipalityAdapterTest {

    IbgeCsvMunicipalityAdapter adapter = new IbgeCsvMunicipalityAdapter();

    @Test
    @DisplayName("Given a known IBGE code, should resolve the municipality coordinates")
    void shouldResolveByIbgeCode() {
        Optional<Coordinates> coordinates = adapter.findByIbgeCode("4202404"); // Blumenau/SC

        assertThat(coordinates).isPresent();
        assertThat(coordinates.get().latitude()).isBetween(-27.1, -26.7);
        assertThat(coordinates.get().longitude()).isBetween(-49.3, -48.9);
    }

    @Test
    @DisplayName("Given an unknown IBGE code, should return empty")
    void shouldReturnEmptyForUnknownCode() {
        assertThat(adapter.findByIbgeCode("9999999")).isEmpty();
    }

    @Test
    @DisplayName("Given a blank IBGE code, should return empty")
    void shouldReturnEmptyForBlankCode() {
        assertThat(adapter.findByIbgeCode("  ")).isEmpty();
        assertThat(adapter.findByIbgeCode(null)).isEmpty();
    }

    @Test
    @DisplayName("Given a known city and state, should resolve coordinates via the fallback index")
    void shouldResolveByCityState() {
        Optional<Coordinates> coordinates = adapter.findByCityState("Blumenau", "SC");

        assertThat(coordinates).isPresent();
        assertThat(coordinates.get().latitude()).isBetween(-27.1, -26.7);
    }

    @Test
    @DisplayName("Given an accented city name, should still resolve via normalization")
    void shouldResolveAccentedCityName() {
        Optional<Coordinates> coordinates = adapter.findByCityState("São Paulo", "SP");

        assertThat(coordinates).isPresent();
        assertThat(coordinates.get().latitude()).isBetween(-23.7, -23.4);
    }
}
