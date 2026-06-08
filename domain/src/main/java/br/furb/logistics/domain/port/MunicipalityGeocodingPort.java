package br.furb.logistics.domain.port;

import br.furb.logistics.domain.model.Coordinates;

import java.util.Optional;

/**
 * Resolve as coordenadas geográficas de um município brasileiro a partir de uma base local
 * (sem dependência externa). A chave primária é o código IBGE do município; o nome+UF é um
 * fallback para CEPs que não retornam o código IBGE.
 */
public interface MunicipalityGeocodingPort {

    Optional<Coordinates> findByIbgeCode(String ibgeCode);

    Optional<Coordinates> findByCityState(String city, String state);
}
