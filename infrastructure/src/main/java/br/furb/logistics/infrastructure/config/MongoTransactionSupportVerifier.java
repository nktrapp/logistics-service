package br.furb.logistics.infrastructure.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/**
 * Fails fast at startup if MongoDB is not a replica set. The transactional outbox/inbox pattern relies on
 * multi-document transactions, which are only available on a replica set (or DocumentDB). Without this guard a
 * standalone mongod would silently commit inbox/outbox writes non-atomically, breaking the reliability guarantees.
 */
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
                            + "Start MongoDB with --replSet (see compose.yml) or use a replica-set connection string.");
        }
        log.info("[startup] MongoDB replica set '{}' detected — multi-document transactions available", setName);
    }
}
