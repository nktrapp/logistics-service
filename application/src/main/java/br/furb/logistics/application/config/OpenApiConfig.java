package br.furb.logistics.application.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String FALLBACK_VERSION = "dev";

    @Bean
    public OpenAPI logisticsOpenAPI(ObjectProvider<BuildProperties> buildProperties) {
        BuildProperties build = buildProperties.getIfAvailable();
        String version = build != null ? build.getVersion() : FALLBACK_VERSION;

        Info info = new Info()
                .title("Logistics Service API")
                .version(version)
                .description("""
                        Serviço de logística do TCC FURB. Gerencia hubs e suas conexões e calcula \
                        rotas de entrega entre hubs (algoritmo de Dijkstra sobre o grafo de hubs), \
                        resolvendo a cidade/estado de origem e destino via ViaCEP. Os cálculos de \
                        rota também são disparados de forma assíncrona ao consumir eventos de pacote \
                        via SQS.""")
                .contact(new Contact()
                        .name("FURB - Trabalho de Conclusão de Curso")
                        .url("https://www.furb.br"))
                .license(new License()
                        .name("Uso acadêmico"));

        Server localServer = new Server()
                .url("http://localhost:8082")
                .description("Ambiente local (docker compose)");

        return new OpenAPI()
                .info(info)
                .servers(List.of(localServer));
    }
}
