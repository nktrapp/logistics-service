package br.furb.logistics.application.config;

import br.furb.logistics.domain.model.Hub;
import br.furb.logistics.domain.model.HubConnection;
import br.furb.logistics.domain.port.HubConnectionRepositoryPort;
import br.furb.logistics.domain.port.HubRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

// Guard de profile em runtime (não @Profile): no nativo as condições @Profile são avaliadas no build-time do AOT e o bean seria podado.
@Slf4j
@Component
@RequiredArgsConstructor
public class HubDataSeeder implements CommandLineRunner {

    private final HubRepositoryPort hubRepository;
    private final HubConnectionRepositoryPort hubConnectionRepository;
    private final Environment environment;

    @Override
    public void run(String... args) {
        if (!environment.matchesProfiles("prod")) {
            return;
        }

        if (!hubRepository.findAllActive().isEmpty()) {
            log.info("[seed] Hubs já cadastrados — seeding ignorado");
            return;
        }

        log.info("[seed] Populando grafo padrão de hubs e conexões");

        Hub blumenau = hubRepository.save(hub("Hub Blumenau", "89010000", "Blumenau", "SC", -26.9194, -49.0661));
        Hub joinville = hubRepository.save(hub("Hub Joinville", "89201000", "Joinville", "SC", -26.3045, -48.8487));
        Hub florianopolis = hubRepository.save(hub("Hub Florianópolis", "88010000", "Florianópolis", "SC", -27.5949, -48.5482));
        Hub curitiba = hubRepository.save(hub("Hub Curitiba", "80010000", "Curitiba", "PR", -25.4284, -49.2733));
        Hub saoPaulo = hubRepository.save(hub("Hub São Paulo", "01310100", "São Paulo", "SP", -23.5614, -46.6559));

        connect(blumenau, joinville, "90.0", 2);
        connect(blumenau, florianopolis, "140.0", 3);
        connect(joinville, curitiba, "130.0", 3);
        connect(florianopolis, curitiba, "300.0", 5);
        connect(curitiba, saoPaulo, "410.0", 6);

        log.info("[seed] Grafo padrão criado: 5 hubs e 5 conexões");
    }

    private Hub hub(String name, String cep, String city, String state, double latitude, double longitude) {
        return Hub.builder()
                .name(name)
                .cep(cep)
                .city(city)
                .state(state)
                .latitude(latitude)
                .longitude(longitude)
                .active(true)
                .build();
    }

    private void connect(Hub origin, Hub destination, String distanceKm, int transitTimeHours) {
        hubConnectionRepository.save(HubConnection.builder()
                .originHubId(origin.getId())
                .destinationHubId(destination.getId())
                .distanceKm(new BigDecimal(distanceKm))
                .transitTimeHours(transitTimeHours)
                .build());
    }
}
