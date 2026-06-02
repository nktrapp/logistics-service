package br.furb.logistics.infrastructure.adapter.out.integration.viacep;

import br.furb.logistics.domain.model.CepInfo;
import br.furb.logistics.domain.port.CepLookupPort;
import br.furb.logistics.infrastructure.adapter.out.integration.cache.RedisCepCacheAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Optional;

import static java.util.Objects.isNull;

@Slf4j
@Component
@RequiredArgsConstructor
public class ViaCepLookupAdapter implements CepLookupPort {

    private final RestClient restClient;
    private final RedisCepCacheAdapter cepCacheAdapter;

    @Value("${app.viacep.retry-max-attempts:3}")
    private int retryMaxAttempts;

    @Value("${app.viacep.retry-backoff-ms:1000}")
    private long retryBackoffMs;

    @Override
    public Optional<CepInfo> findByCep(String cep) {
        log.info("[viacep] Looking up CEP {}", cep);

        Optional<CepInfo> cached = cepCacheAdapter.get(cep);
        if (cached.isPresent()) {
            log.debug("[viacep] Cache hit for CEP {}", cep);
            return cached;
        }

        int attempts = Math.max(retryMaxAttempts, 1);

        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                ViaCepResponse response = restClient.get()
                        .uri("/{cep}/json", cep)
                        .retrieve()
                        .body(ViaCepResponse.class);

                if (isNull(response) || response.isErro()) {
                    log.warn("[viacep] CEP {} not found or invalid", cep);
                    return Optional.empty();
                }

                CepInfo cepInfo = ViaCepMapper.toDomain(response);
                cepCacheAdapter.put(cep, cepInfo);

                log.debug("[viacep] CEP {} resolved to {}/{}", cep, cepInfo.getCity(), cepInfo.getState());
                return Optional.of(cepInfo);
            } catch (RestClientResponseException e) {
                if (e.getStatusCode().is4xxClientError()) {
                    log.warn("[viacep] CEP {} rejected by ViaCEP with status {}", cep, e.getStatusCode());
                    return Optional.empty();
                }

                if (attempt == attempts) {
                    throw new IllegalStateException("Failed to look up CEP after retries: " + cep, e);
                }

                waitBeforeRetry(cep, attempt, e);
            } catch (ResourceAccessException e) {
                if (attempt == attempts) {
                    throw new IllegalStateException("Failed to look up CEP after retries: " + cep, e);
                }

                waitBeforeRetry(cep, attempt, e);
            }
        }

        throw new IllegalStateException("Failed to look up CEP after retries: " + cep);
    }

    private void waitBeforeRetry(String cep, int attempt, Exception exception) {
        long backoffDelayMs = retryBackoffMs * attempt;
        log.warn("[viacep] Error looking up CEP {} on attempt {}, retrying in {} ms", cep, attempt, backoffDelayMs, exception);

        try {
            Thread.sleep(backoffDelayMs);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Retry interrupted while looking up CEP: " + cep, interruptedException);
        }
    }
}
