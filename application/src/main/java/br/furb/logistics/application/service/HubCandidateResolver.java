package br.furb.logistics.application.service;

import br.furb.logistics.domain.model.CepInfo;
import br.furb.logistics.domain.model.Coordinates;
import br.furb.logistics.domain.model.Hub;
import br.furb.logistics.domain.port.MunicipalityGeocodingPort;
import br.furb.logistics.domain.util.HaversineCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.springframework.util.CollectionUtils.isEmpty;

@Slf4j
@RequiredArgsConstructor
public class HubCandidateResolver {

    private final MunicipalityGeocodingPort municipalityGeocodingPort;
    private final int maxNearest;

    public List<Hub> resolve(CepInfo cepInfo, List<Hub> activeHubs) {
        Optional<Coordinates> coordinates = geocode(cepInfo);
        if (coordinates.isPresent()) {
            List<Hub> nearest = nearestHubs(coordinates.get(), activeHubs);
            if (!isEmpty(nearest)) {
                return nearest;
            }
        }

        return cityThenStateCandidates(cepInfo.getCity(), cepInfo.getState(), activeHubs);
    }

    private Optional<Coordinates> geocode(CepInfo cepInfo) {
        Optional<Coordinates> byIbgeCode = municipalityGeocodingPort.findByIbgeCode(cepInfo.getIbgeCode());
        if (byIbgeCode.isPresent()) {
            return byIbgeCode;
        }
        return municipalityGeocodingPort.findByCityState(cepInfo.getCity(), cepInfo.getState());
    }

    private List<Hub> nearestHubs(Coordinates origin, List<Hub> activeHubs) {
        return activeHubs.stream()
                .filter(this::hasCoordinates)
                .sorted(Comparator
                        .comparingDouble((Hub hub) -> HaversineCalculator.distanceKm(origin, coordinatesOf(hub)))
                        .thenComparing(Hub::getId))
                .limit(maxNearest)
                .toList();
    }

    private List<Hub> cityThenStateCandidates(String city, String state, List<Hub> activeHubs) {
        List<Hub> cityCandidates = activeHubs.stream()
                .filter(hub -> state.equalsIgnoreCase(hub.getState()) && city.equalsIgnoreCase(hub.getCity()))
                .toList();
        if (!isEmpty(cityCandidates)) {
            return cityCandidates;
        }

        return activeHubs.stream()
                .filter(hub -> state.equalsIgnoreCase(hub.getState()))
                .toList();
    }

    private Coordinates coordinatesOf(Hub hub) {
        return new Coordinates(hub.getLatitude(), hub.getLongitude());
    }

    private boolean hasCoordinates(Hub hub) {
        return hub.getLatitude() != 0.0 || hub.getLongitude() != 0.0;
    }
}
