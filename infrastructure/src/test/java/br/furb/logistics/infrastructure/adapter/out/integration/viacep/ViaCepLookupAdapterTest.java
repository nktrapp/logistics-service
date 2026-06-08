package br.furb.logistics.infrastructure.adapter.out.integration.viacep;

import br.furb.logistics.domain.model.CepInfo;
import br.furb.logistics.infrastructure.adapter.out.integration.cache.RedisCepCacheAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
@DisplayName("ViaCepLookupAdapter")
class ViaCepLookupAdapterTest {

    private static final String CEP = "89010000";
    private static final String URL = "http://viacep.test/ws/" + CEP + "/json";
    private static final String JSON = "{\"cep\":\"89010-000\",\"logradouro\":\"Rua X\",\"bairro\":\"Centro\",\"localidade\":\"Blumenau\",\"uf\":\"SC\"}";

    @Mock
    RedisCepCacheAdapter cepCacheAdapter;

    private RestClient restClient;
    private MockRestServiceServer server;
    private ViaCepLookupAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://viacep.test/ws");
        server = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
        adapter = new ViaCepLookupAdapter(restClient, cepCacheAdapter);
        ReflectionTestUtils.setField(adapter, "retryMaxAttempts", 3);
        ReflectionTestUtils.setField(adapter, "retryBackoffMs", 1L);
    }

    @Test
    @DisplayName("Given a cached CEP, should return it without calling ViaCEP")
    void shouldReturnCachedWithoutHttpCall() {
        CepInfo cached = CepInfo.builder().cep(CEP).city("Blumenau").state("SC").neighborhood("Centro").build();
        when(cepCacheAdapter.get(CEP)).thenReturn(Optional.of(cached));

        Optional<CepInfo> result = adapter.findByCep(CEP);

        assertThat(result).contains(cached);
        verify(cepCacheAdapter, never()).put(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
        server.verify();
    }

    @Test
    @DisplayName("Given a cache miss, should fetch from ViaCEP, map and cache the result")
    void shouldFetchMapAndCache() {
        when(cepCacheAdapter.get(CEP)).thenReturn(Optional.empty());
        server.expect(requestTo(URL)).andRespond(withSuccess(JSON, MediaType.APPLICATION_JSON));

        Optional<CepInfo> result = adapter.findByCep(CEP);

        assertThat(result).isPresent();
        assertThat(result.get().getCity()).isEqualTo("Blumenau");
        assertThat(result.get().getState()).isEqualTo("SC");
        assertThat(result.get().getCep()).isEqualTo(CEP);
        verify(cepCacheAdapter).put(CEP, result.get());
        server.verify();
    }

    @Test
    @DisplayName("Given a ViaCEP 'erro' response, should return empty")
    void shouldReturnEmptyWhenViaCepReportsError() {
        when(cepCacheAdapter.get(CEP)).thenReturn(Optional.empty());
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"erro\":true}", MediaType.APPLICATION_JSON));

        assertThat(adapter.findByCep(CEP)).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("Given a 4xx response, should return empty without retrying")
    void shouldReturnEmptyOnClientError() {
        when(cepCacheAdapter.get(CEP)).thenReturn(Optional.empty());
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(adapter.findByCep(CEP)).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("Given a transient 5xx, should retry and succeed on a later attempt")
    void shouldRetryOnServerErrorThenSucceed() {
        when(cepCacheAdapter.get(CEP)).thenReturn(Optional.empty());
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        server.expect(requestTo(URL)).andRespond(withSuccess(JSON, MediaType.APPLICATION_JSON));

        Optional<CepInfo> result = adapter.findByCep(CEP);

        assertThat(result).isPresent();
        assertThat(result.get().getCity()).isEqualTo("Blumenau");
        server.verify();
    }

    @Test
    @DisplayName("Given 5xx on every attempt, should fail after exhausting the retries")
    void shouldFailAfterExhaustingRetries() {
        ReflectionTestUtils.setField(adapter, "retryMaxAttempts", 2);
        when(cepCacheAdapter.get(CEP)).thenReturn(Optional.empty());
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> adapter.findByCep(CEP))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to look up CEP after retries");
        server.verify();
    }
}
