package br.furb.logistics.domain.util;

import br.furb.logistics.domain.model.Coordinates;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HaversineCalculator")
class HaversineCalculatorTest {

    @Test
    @DisplayName("Given two known cities, should compute the great-circle distance within tolerance")
    void shouldComputeKnownDistance() {
        Coordinates blumenau = new Coordinates(-26.9194, -49.0661);
        Coordinates joinville = new Coordinates(-26.3045, -48.8487);

        double distance = HaversineCalculator.distanceKm(blumenau, joinville);

        assertThat(distance).isBetween(65.0, 80.0);
    }

    @Test
    @DisplayName("Given the same point twice, should return zero distance")
    void shouldReturnZeroForSamePoint() {
        Coordinates point = new Coordinates(-26.9194, -49.0661);

        assertThat(HaversineCalculator.distanceKm(point, point)).isZero();
    }

    @Test
    @DisplayName("Should be symmetric regardless of argument order")
    void shouldBeSymmetric() {
        Coordinates blumenau = new Coordinates(-26.9194, -49.0661);
        Coordinates curitiba = new Coordinates(-25.4284, -49.2733);

        assertThat(HaversineCalculator.distanceKm(blumenau, curitiba))
                .isEqualTo(HaversineCalculator.distanceKm(curitiba, blumenau));
    }
}
