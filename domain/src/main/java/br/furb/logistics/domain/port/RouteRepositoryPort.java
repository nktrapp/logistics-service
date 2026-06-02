package br.furb.logistics.domain.port;

import br.furb.logistics.domain.model.Route;

import java.util.Optional;

public interface RouteRepositoryPort {

    Route save(Route route);

    Optional<Route> findById(String id);

    Optional<Route> findByPackageId(String packageId);
}
