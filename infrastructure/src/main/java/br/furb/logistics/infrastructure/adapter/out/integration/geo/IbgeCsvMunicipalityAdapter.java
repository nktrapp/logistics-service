package br.furb.logistics.infrastructure.adapter.out.integration.geo;

import br.furb.logistics.domain.model.Coordinates;
import br.furb.logistics.domain.port.MunicipalityGeocodingPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@ImportRuntimeHints(IbgeCsvMunicipalityAdapter.GeoResourceHints.class)
public class IbgeCsvMunicipalityAdapter implements MunicipalityGeocodingPort {

    private static final String CSV_RESOURCE = "geo/municipios.csv";

    static class GeoResourceHints implements RuntimeHintsRegistrar {
        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            hints.resources().registerPattern(CSV_RESOURCE);
        }
    }

    private static final Map<String, String> UF_BY_IBGE_CODE = Map.ofEntries(
            Map.entry("11", "RO"), Map.entry("12", "AC"), Map.entry("13", "AM"), Map.entry("14", "RR"),
            Map.entry("15", "PA"), Map.entry("16", "AP"), Map.entry("17", "TO"), Map.entry("21", "MA"),
            Map.entry("22", "PI"), Map.entry("23", "CE"), Map.entry("24", "RN"), Map.entry("25", "PB"),
            Map.entry("26", "PE"), Map.entry("27", "AL"), Map.entry("28", "SE"), Map.entry("29", "BA"),
            Map.entry("31", "MG"), Map.entry("32", "ES"), Map.entry("33", "RJ"), Map.entry("35", "SP"),
            Map.entry("41", "PR"), Map.entry("42", "SC"), Map.entry("43", "RS"), Map.entry("50", "MS"),
            Map.entry("51", "MT"), Map.entry("52", "GO"), Map.entry("53", "DF"));

    private final Map<String, Coordinates> coordinatesByIbgeCode = new HashMap<>();
    private final Map<String, Coordinates> coordinatesByCityState = new HashMap<>();

    public IbgeCsvMunicipalityAdapter() {
        load();
    }

    @Override
    public Optional<Coordinates> findByIbgeCode(String ibgeCode) {
        if (!StringUtils.hasText(ibgeCode)) {
            return Optional.empty();
        }
        return Optional.ofNullable(coordinatesByIbgeCode.get(ibgeCode.trim()));
    }

    @Override
    public Optional<Coordinates> findByCityState(String city, String state) {
        if (!StringUtils.hasText(city) || !StringUtils.hasText(state)) {
            return Optional.empty();
        }
        return Optional.ofNullable(coordinatesByCityState.get(cityStateKey(city, state)));
    }

    private void load() {
        ClassPathResource resource = new ClassPathResource(CSV_RESOURCE);
        if (!resource.exists()) {
            throw new IllegalStateException("IBGE municipality dataset not found on classpath: " + CSV_RESOURCE);
        }

        try (InputStream inputStream = resource.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            reader.readLine(); // header
            String line;
            while ((line = reader.readLine()) != null) {
                parseLine(line);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load IBGE municipality dataset: " + CSV_RESOURCE, e);
        }

        log.info("[ibge-geocoding] Loaded {} municipalities from {}", coordinatesByIbgeCode.size(), CSV_RESOURCE);
    }

    private void parseLine(String line) {
        if (!StringUtils.hasText(line)) {
            return;
        }

        String[] columns = line.split(",");
        if (columns.length < 6) {
            return;
        }

        String ibgeCode = columns[0].trim();
        String name = columns[1].trim();
        Coordinates coordinates = new Coordinates(
                Double.parseDouble(columns[2].trim()),
                Double.parseDouble(columns[3].trim()));
        String uf = UF_BY_IBGE_CODE.get(columns[5].trim());

        coordinatesByIbgeCode.put(ibgeCode, coordinates);
        if (uf != null) {
            coordinatesByCityState.put(cityStateKey(name, uf), coordinates);
        }
    }

    private String cityStateKey(String city, String state) {
        return normalize(city) + "|" + state.trim().toUpperCase();
    }

    private String normalize(String value) {
        String withoutAccents = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return withoutAccents.toLowerCase();
    }
}
