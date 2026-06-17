package br.furb.logistics.domain.port;

import br.furb.logistics.domain.model.Coordinates;

import java.util.Optional;

public interface MunicipalityGeocodingPort {

    Optional<Coordinates> findByIbgeCode(String ibgeCode);

    Optional<Coordinates> findByCityState(String city, String state);
}
