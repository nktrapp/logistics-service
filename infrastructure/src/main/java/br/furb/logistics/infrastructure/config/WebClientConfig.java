package br.furb.logistics.infrastructure.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class WebClientConfig {

    @Value("${app.viacep.base-url:https://viacep.com.br/ws}")
    private String viaCepBaseUrl;

    @Value("${app.viacep.timeout-ms:5000}")
    private int timeoutMs;

    @Bean
    public RestClient restClient(ObjectProvider<RestClient.Builder> restClientBuilderProvider) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);

        RestClient.Builder restClientBuilder = restClientBuilderProvider.getIfAvailable(RestClient::builder);
        return restClientBuilder
                .baseUrl(viaCepBaseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
