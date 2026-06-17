package br.furb.logistics.infrastructure.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

// Falha no startup se o Mongo não for replica set: as transações multi-documento do outbox/inbox dependem disso.
@Slf4j
@Component
@RequiredArgsConstructor
public class MongoTransactionSupportVerifier {

    private final MongoTemplate mongoTemplate;

    @PostConstruct
    void verifyReplicaSet() {
        Document hello = mongoTemplate.executeCommand(new Document("hello", 1));
        Object setName = hello.get("setName");
        if (setName == null) {
            throw new IllegalStateException(
                    "MongoDB is not running as a replica set; multi-document transactions are unavailable. "
                            + "The transactional outbox/inbox pattern requires them. "
                            + "Start MongoDB with --replSet or use a replica-set connection string.");
        }
        log.info("[startup] MongoDB replica set '{}' detected — multi-document transactions available", setName);
    }
}
