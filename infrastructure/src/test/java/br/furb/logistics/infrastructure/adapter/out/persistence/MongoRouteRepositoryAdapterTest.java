package br.furb.logistics.infrastructure.adapter.out.persistence;

import br.furb.logistics.domain.model.Route;
import br.furb.logistics.domain.model.RouteStatus;
import br.furb.logistics.domain.port.RouteRepositoryPort;
import br.furb.logistics.infrastructure.adapter.out.persistence.repository.MongoRouteRepositoryAdapter;
import br.furb.logistics.infrastructure.adapter.out.persistence.repository.mongo.RouteMongoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration;
import org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = MongoRouteRepositoryAdapterTest.TestConfig.class)
@DisplayName("MongoRouteRepositoryAdapter")
class MongoRouteRepositoryAdapterTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:8.0")).withReplicaSet();

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("spring.data.mongodb.auto-index-creation", () -> true);
    }

    @Autowired
    RouteRepositoryPort routeRepository;
    @Autowired
    MongoTemplate mongoTemplate;

    @BeforeEach
    void clear() {
        mongoTemplate.getCollectionNames().forEach(collection -> mongoTemplate.remove(new Query(), collection));
    }

    @Test
    @DisplayName("saves a route assigning an id and reads it back by id")
    void shouldSaveAndFindById() {
        Route saved = routeRepository.save(buildRoute());

        assertThat(saved.getId()).isNotBlank();

        Optional<Route> found = routeRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getPackageId()).isEqualTo("pkg-1");
        assertThat(found.get().getStatus()).isEqualTo(RouteStatus.CALCULATED);
    }

    @Test
    @DisplayName("reads a route back by its packageId")
    void shouldFindByPackageId() {
        routeRepository.save(buildRoute());

        Optional<Route> found = routeRepository.findByPackageId("pkg-1");
        assertThat(found).isPresent();
        assertThat(found.get().getTotalDistanceKm()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("returns empty for an unknown id")
    void shouldReturnEmptyForUnknownId() {
        assertThat(routeRepository.findById("missing")).isEmpty();
    }

    private Route buildRoute() {
        return Route.builder()
                .packageId("pkg-1")
                .originHubId("origin-1")
                .destinationHubId("dest-1")
                .hops(List.of(Route.RouteHop.builder().hubId("origin-1").hubName("Origin").order(1).build()))
                .totalDistanceKm(50.0)
                .estimatedTransitHours(6)
                .status(RouteStatus.CALCULATED)
                .createdAt(Instant.parse("2026-05-31T10:00:00Z"))
                .build();
    }

    @SpringBootConfiguration
    @ImportAutoConfiguration({MongoAutoConfiguration.class, DataMongoAutoConfiguration.class})
    @EnableMongoRepositories(basePackageClasses = RouteMongoRepository.class)
    @Import(MongoRouteRepositoryAdapter.class)
    static class TestConfig {
    }
}
