package br.furb.logistics.infrastructure.adapter.out.persistence;

import br.furb.logistics.domain.model.Hub;
import br.furb.logistics.domain.port.HubRepositoryPort;
import br.furb.logistics.infrastructure.adapter.out.persistence.repository.MongoHubRepositoryAdapter;
import br.furb.logistics.infrastructure.adapter.out.persistence.repository.mongo.HubMongoRepository;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = MongoHubRepositoryAdapterTest.TestConfig.class)
@DisplayName("MongoHubRepositoryAdapter")
class MongoHubRepositoryAdapterTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:8.0")).withReplicaSet();

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("spring.data.mongodb.auto-index-creation", () -> true);
    }

    @Autowired
    HubRepositoryPort hubRepository;
    @Autowired
    MongoTemplate mongoTemplate;

    @BeforeEach
    void clear() {
        mongoTemplate.getCollectionNames().forEach(collection -> mongoTemplate.remove(new Query(), collection));
    }

    @Test
    @DisplayName("saves a hub assigning an id and reads it back by id")
    void shouldSaveAndFindById() {
        Hub saved = hubRepository.save(buildHub("Hub Centro", "89010000", "Blumenau", "SC", true));

        assertThat(saved.getId()).isNotBlank();

        Optional<Hub> found = hubRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Hub Centro");
        assertThat(found.get().isActive()).isTrue();
    }

    @Test
    @DisplayName("findAllActive returns only active hubs")
    void shouldReturnOnlyActiveHubs() {
        hubRepository.save(buildHub("Active Hub", "89010001", "Blumenau", "SC", true));
        hubRepository.save(buildHub("Inactive Hub", "89010002", "Joinville", "SC", false));

        List<Hub> active = hubRepository.findAllActive();

        assertThat(active).hasSize(1);
        assertThat(active.getFirst().getName()).isEqualTo("Active Hub");
        assertThat(hubRepository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("findByCityAndState filters hubs by city and state")
    void shouldFindByCityAndState() {
        hubRepository.save(buildHub("Hub BNU", "89010003", "Blumenau", "SC", true));
        hubRepository.save(buildHub("Hub JOI", "89010004", "Joinville", "SC", true));

        List<Hub> result = hubRepository.findByCityAndState("Blumenau", "SC");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getName()).isEqualTo("Hub BNU");
    }

    private Hub buildHub(String name, String cep, String city, String state, boolean active) {
        return Hub.builder()
                .name(name)
                .cep(cep)
                .city(city)
                .state(state)
                .active(active)
                .build();
    }

    @SpringBootConfiguration
    @ImportAutoConfiguration({MongoAutoConfiguration.class, DataMongoAutoConfiguration.class})
    @EnableMongoRepositories(basePackageClasses = HubMongoRepository.class)
    @Import(MongoHubRepositoryAdapter.class)
    static class TestConfig {
    }
}
