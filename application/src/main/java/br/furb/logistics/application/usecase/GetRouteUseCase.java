package br.furb.logistics.application.usecase;

import br.furb.logistics.application.dto.RouteResponse;
import br.furb.logistics.application.mapper.RouteMapper;
import br.furb.logistics.domain.port.RouteRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class GetRouteUseCase {

    private final RouteRepositoryPort routeRepository;

    public Optional<RouteResponse> findById(String routeId) {
        log.info("[get-route] Fetching route by id {}", routeId);
        return routeRepository.findById(routeId).map(RouteMapper.INSTANCE::toResponse);
    }

    public Optional<RouteResponse> findByPackageId(String packageId) {
        log.info("[get-route] Fetching route by packageId {}", packageId);
        return routeRepository.findByPackageId(packageId).map(RouteMapper.INSTANCE::toResponse);
    }
}
