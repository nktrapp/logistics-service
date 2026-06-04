package br.furb.logistics.infrastructure.adapter.out.integration.cache;

import br.furb.logistics.domain.model.CepInfo;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisCepCacheAdapter {

    private static final String KEY_PREFIX = "cep:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.redis.cep-cache.ttl-hours:24}")
    private int ttlHours;

    public Optional<CepInfo> get(String cep) {
        try {
            String json = redisTemplate.opsForValue().get(KEY_PREFIX + cep);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, CepInfo.class));
        } catch (Exception e) {
            log.warn("[redis-cache] Error reading cache for CEP {}", cep, e);
            return Optional.empty();
        }
    }

    public void put(String cep, CepInfo cepInfo) {
        try {
            String json = objectMapper.writeValueAsString(cepInfo);
            redisTemplate.opsForValue().set(KEY_PREFIX + cep, json, Duration.ofHours(ttlHours));
        } catch (Exception e) {
            log.warn("[redis-cache] Error writing cache for CEP {}", cep, e);
        }
    }
}
