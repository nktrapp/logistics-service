package br.furb.logistics.domain.util;

import br.furb.logistics.domain.model.Coordinates;

/**
 * Calcula a distância geográfica (great-circle) entre dois pontos pela fórmula de Haversine.
 */
public final class HaversineCalculator {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    private HaversineCalculator() {
    }

    public static double distanceKm(Coordinates origin, Coordinates destination) {
        double originLatRad = Math.toRadians(origin.latitude());
        double destinationLatRad = Math.toRadians(destination.latitude());
        double deltaLatRad = Math.toRadians(destination.latitude() - origin.latitude());
        double deltaLonRad = Math.toRadians(destination.longitude() - origin.longitude());

        double a = Math.sin(deltaLatRad / 2) * Math.sin(deltaLatRad / 2)
                + Math.cos(originLatRad) * Math.cos(destinationLatRad)
                * Math.sin(deltaLonRad / 2) * Math.sin(deltaLonRad / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_KM * c;
    }
}
