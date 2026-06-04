package br.furb.logistics.infrastructure.adapter.out.integration.cache;

import br.furb.logistics.domain.model.CepInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisCepCacheAdapterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    void shouldDeserializeCepInfoFromCache() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("cep:89201000"))
                .thenReturn("{\"cep\":\"89201000\",\"city\":\"Joinville\",\"state\":\"SC\",\"neighborhood\":\"Centro\"}");

        RedisCepCacheAdapter adapter = new RedisCepCacheAdapter(redisTemplate, new ObjectMapper());

        Optional<CepInfo> cached = adapter.get("89201000");

        assertThat(cached).isPresent();
        assertThat(cached.get().getCep()).isEqualTo("89201000");
        assertThat(cached.get().getCity()).isEqualTo("Joinville");
        assertThat(cached.get().getState()).isEqualTo("SC");
        assertThat(cached.get().getNeighborhood()).isEqualTo("Centro");
    }

    @Test
    void shouldSerializeCepInfoWithConfiguredTtl() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        ObjectMapper objectMapper = new ObjectMapper();
        RedisCepCacheAdapter adapter = new RedisCepCacheAdapter(redisTemplate, objectMapper);
        ReflectionTestUtils.setField(adapter, "ttlHours", 6);

        adapter.put("89201000", CepInfo.builder()
                .cep("89201000")
                .city("Joinville")
                .state("SC")
                .neighborhood("Centro")
                .build());

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq("cep:89201000"), jsonCaptor.capture(), eq(Duration.ofHours(6)));
        assertThat(objectMapper.readTree(jsonCaptor.getValue()).get("city").asText()).isEqualTo("Joinville");
    }

    @Test
    void shouldIgnoreCacheWriteErrors() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(new RuntimeException("redis unavailable"))
                .when(valueOperations)
                .set(eq("cep:89201000"), anyString(), eq(Duration.ofHours(24)));

        RedisCepCacheAdapter adapter = new RedisCepCacheAdapter(redisTemplate, new ObjectMapper());

        assertThatCode(() -> adapter.put("89201000", CepInfo.builder()
                .cep("89201000")
                .build()))
                .doesNotThrowAnyException();
    }
}
