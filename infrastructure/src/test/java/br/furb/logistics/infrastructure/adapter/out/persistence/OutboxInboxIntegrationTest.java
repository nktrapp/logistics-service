package br.furb.logistics.infrastructure.adapter.out.persistence;

import br.furb.logistics.domain.event.RouteFailedEvent;
import br.furb.logistics.domain.port.InboxRepositoryPort;
import br.furb.logistics.domain.port.OutboxRepositoryPort;
import br.furb.logistics.domain.port.OutboxRepositoryPort.OutboxEntry;
import br.furb.logistics.domain.port.OutboxRepositoryPort.RetryOutcome;
import br.furb.logistics.infrastructure.adapter.out.persistence.document.OutboxDocument;
import br.furb.logistics.infrastructure.adapter.out.persistence.repository.MongoInboxRepositoryAdapter;
import br.furb.logistics.infrastructure.adapter.out.persistence.repository.MongoOutboxRepositoryAdapter;
import br.furb.logistics.infrastructure.adapter.out.persistence.repository.mongo.OutboxMongoRepository;
import br.furb.logistics.infrastructure.config.TraceContextSupport;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration;
import org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = OutboxInboxIntegrationTest.TestConfig.class)
@DisplayName("Outbox/Inbox on a MongoDB replica set")
class OutboxInboxIntegrationTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:8.0")).withReplicaSet();

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("spring.data.mongodb.auto-index-creation", () -> true);
    }

    @Autowired
    OutboxRepositoryPort outboxRepository;
    @Autowired
    InboxRepositoryPort inboxRepository;
    @Autowired
    TransactionTemplate transactionTemplate;
    @Autowired
    MongoTemplate mongoTemplate;

    @BeforeEach
    void clear() {
        mongoTemplate.getCollectionNames().forEach(collection -> mongoTemplate.remove(new Query(), collection));
    }

    @Test
    @DisplayName("persists an event, claims it, publishes it, then purges it")
    void outboxRoundTrip() {
        RouteFailedEvent event = sampleEvent();
        outboxRepository.save(event);

        Instant now = Instant.now();
        List<OutboxEntry> claimed = outboxRepository.claimPending(10, now, now.minusSeconds(60));

        assertThat(claimed).hasSize(1);
        OutboxEntry entry = claimed.getFirst();
        assertThat(entry.eventId()).isEqualTo(event.getEventId());
        assertThat(entry.eventType()).isEqualTo("route.failed");
        assertThat(entry.groupId()).isEqualTo("pkg-1");
        assertThat(entry.traceparent()).isEqualTo("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        assertThat(entry.tracestate()).isEqualTo("rojo=00f067aa0ba902b7");

        outboxRepository.markAsPublished(entry.eventId(), Instant.now());
        assertThat(outboxRepository.countFailed()).isZero();

        long deleted = outboxRepository.deletePublishedBefore(Instant.now().plusSeconds(1));
        assertThat(deleted).isEqualTo(1);
    }

    @Test
    @DisplayName("schedules retries while attempts remain and marks the event FAILED once exhausted")
    void outboxRetryExhaustion() {
        RouteFailedEvent event = sampleEvent();
        outboxRepository.save(event);
        String eventId = event.getEventId();
        Instant nextAttempt = Instant.now().plusSeconds(30);

        assertThat(outboxRepository.markForRetry(eventId, "boom", nextAttempt, 3).retryScheduled()).isTrue();
        assertThat(outboxRepository.markForRetry(eventId, "boom", nextAttempt, 3).retryScheduled()).isTrue();
        RetryOutcome exhausted = outboxRepository.markForRetry(eventId, "boom", nextAttempt, 3);

        assertThat(exhausted.retryScheduled()).isFalse();
        assertThat(exhausted.retryCount()).isEqualTo(3);
        assertThat(outboxRepository.countFailed()).isEqualTo(1);
    }

    @Test
    @DisplayName("does not re-claim an in-progress event until its processing has timed out")
    void outboxReclaimsOnlyAfterTimeout() {
        outboxRepository.save(sampleEvent());

        Instant first = Instant.now();
        assertThat(outboxRepository.claimPending(10, first, first.minusSeconds(60))).hasSize(1);

        Instant beforeTimeout = first.plusSeconds(30);
        assertThat(outboxRepository.claimPending(10, beforeTimeout, beforeTimeout.minusSeconds(60))).isEmpty();

        Instant afterTimeout = first.plusSeconds(120);
        assertThat(outboxRepository.claimPending(10, afterTimeout, first.plusSeconds(60))).hasSize(1);
    }

    @Test
    @DisplayName("inbox saveIfAbsent is idempotent on the unique eventId")
    void inboxIdempotency() {
        assertThat(inboxRepository.saveIfAbsent("evt-1", "package.created")).isTrue();
        assertThat(inboxRepository.saveIfAbsent("evt-1", "package.created")).isFalse();
        assertThat(inboxRepository.existsByEventId("evt-1")).isTrue();
    }

    @Test
    @DisplayName("a failed transaction rolls back the inbox claim so the event is reprocessed")
    void rollbackReprocessesEvent() {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            inboxRepository.saveIfAbsent("evt-2", "package.created");
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(inboxRepository.existsByEventId("evt-2")).isFalse();

        transactionTemplate.executeWithoutResult(status ->
                assertThat(inboxRepository.saveIfAbsent("evt-2", "package.created")).isTrue());
        assertThat(inboxRepository.existsByEventId("evt-2")).isTrue();
    }

    @Test
    @DisplayName("an earlier unpublished sibling of the same group blocks the check until it is published")
    void earlierUnpublishedSiblingBlocksUntilPublished() {
        RouteFailedEvent firstEvent = sampleEvent();
        RouteFailedEvent secondEvent = sampleEvent();
        outboxRepository.save(firstEvent);
        outboxRepository.save(secondEvent);

        Instant now = Instant.now();
        List<OutboxEntry> claimed = outboxRepository.claimPending(10, now, now.minusSeconds(60));
        assertThat(claimed).hasSize(2);
        OutboxEntry head = findByEventId(claimed, firstEvent.getEventId());
        OutboxEntry tail = findByEventId(claimed, secondEvent.getEventId());

        outboxRepository.markForRetry(head.eventId(), "boom", Instant.now().plusSeconds(30), 5);
        assertThat(outboxRepository.existsEarlierUnpublished("pkg-1", tail.createdAt(), tail.id())).isTrue();

        outboxRepository.markAsPublished(head.eventId(), Instant.now());
        assertThat(outboxRepository.existsEarlierUnpublished("pkg-1", tail.createdAt(), tail.id())).isFalse();
    }

    @Test
    @DisplayName("a FAILED head keeps blocking the whole group until manual replay")
    void failedHeadBlocksGroup() {
        RouteFailedEvent firstEvent = sampleEvent();
        RouteFailedEvent secondEvent = sampleEvent();
        outboxRepository.save(firstEvent);
        outboxRepository.save(secondEvent);

        Instant now = Instant.now();
        List<OutboxEntry> claimed = outboxRepository.claimPending(10, now, now.minusSeconds(60));
        assertThat(claimed).hasSize(2);
        OutboxEntry head = findByEventId(claimed, firstEvent.getEventId());
        OutboxEntry tail = findByEventId(claimed, secondEvent.getEventId());

        RetryOutcome exhausted = outboxRepository.markForRetry(head.eventId(), "boom", Instant.now().plusSeconds(30), 1);
        assertThat(exhausted.retryScheduled()).isFalse();

        assertThat(outboxRepository.existsEarlierUnpublished("pkg-1", tail.createdAt(), tail.id())).isTrue();
    }

    @Test
    @DisplayName("an event of a different group is not blocked by a pending event of another group")
    void differentGroupIsNotBlocked() {
        outboxRepository.save(sampleEvent());
        RouteFailedEvent otherGroupEvent = sampleEvent("pkg-2");
        outboxRepository.save(otherGroupEvent);

        Query otherQuery = Query.query(Criteria.where("eventId").is(otherGroupEvent.getEventId()));
        OutboxDocument otherDocument = mongoTemplate.findOne(otherQuery, OutboxDocument.class);
        assertThat(otherDocument).isNotNull();

        boolean blocked = outboxRepository.existsEarlierUnpublished("pkg-2", otherDocument.getCreatedAt(), otherDocument.getId());
        assertThat(blocked).isFalse();
    }

    @Test
    @DisplayName("releaseClaim returns the entry to PENDING without counting a retry")
    void releaseClaimReturnsEntryToPendingWithoutCountingRetry() {
        RouteFailedEvent event = sampleEvent();
        outboxRepository.save(event);

        Instant now = Instant.now();
        assertThat(outboxRepository.claimPending(10, now, now.minusSeconds(60))).hasSize(1);

        outboxRepository.releaseClaim(event.getEventId(), now.plusSeconds(5));

        Query query = Query.query(Criteria.where("eventId").is(event.getEventId()));
        OutboxDocument document = mongoTemplate.findOne(query, OutboxDocument.class);
        assertThat(document).isNotNull();
        assertThat(document.getStatus()).isEqualTo("PENDING");
        assertThat(document.getRetryCount()).isZero();
        assertThat(document.getProcessingStartedAt()).isNull();
        assertThat(document.getNextAttemptAt()).isNotNull();

        assertThat(outboxRepository.claimPending(10, Instant.now(), Instant.now().minusSeconds(60))).isEmpty();
    }

    @Test
    @DisplayName("releaseClaim is a no-op on an already published entry")
    void releaseClaimIsNoOpOnPublishedEntry() {
        RouteFailedEvent event = sampleEvent();
        outboxRepository.save(event);

        Instant now = Instant.now();
        assertThat(outboxRepository.claimPending(10, now, now.minusSeconds(60))).hasSize(1);
        outboxRepository.markAsPublished(event.getEventId(), Instant.now());

        outboxRepository.releaseClaim(event.getEventId(), Instant.now().plusSeconds(5));

        Query query = Query.query(Criteria.where("eventId").is(event.getEventId()));
        OutboxDocument document = mongoTemplate.findOne(query, OutboxDocument.class);
        assertThat(document).isNotNull();
        assertThat(document.getStatus()).isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("ties on createdAt are broken by _id so only the later document is blocked")
    void tieBreaksOnObjectIdWhenCreatedAtIsEqual() {
        Instant sharedCreatedAt = Instant.parse("2026-05-31T10:00:00Z");
        OutboxDocument smaller = OutboxDocument.builder()
                .id("65f000000000000000000001")
                .eventId("tie-evt-1")
                .eventType("route.failed")
                .payload("{}")
                .groupId("grp-tie")
                .status("PENDING")
                .nextAttemptAt(sharedCreatedAt)
                .retryCount(0)
                .createdAt(sharedCreatedAt)
                .build();
        OutboxDocument larger = OutboxDocument.builder()
                .id("65f000000000000000000002")
                .eventId("tie-evt-2")
                .eventType("route.failed")
                .payload("{}")
                .groupId("grp-tie")
                .status("PENDING")
                .nextAttemptAt(sharedCreatedAt)
                .retryCount(0)
                .createdAt(sharedCreatedAt)
                .build();
        mongoTemplate.insert(smaller);
        mongoTemplate.insert(larger);

        assertThat(outboxRepository.existsEarlierUnpublished("grp-tie", sharedCreatedAt, larger.getId())).isTrue();
        assertThat(outboxRepository.existsEarlierUnpublished("grp-tie", sharedCreatedAt, smaller.getId())).isFalse();
    }

    @Test
    @DisplayName("concurrent claimers cannot invert per-group publish order")
    void concurrentClaimersPreservePerGroupOrder() throws InterruptedException {
        RouteFailedEvent firstEvent = sampleEvent();
        RouteFailedEvent secondEvent = sampleEvent();
        outboxRepository.save(firstEvent);
        outboxRepository.save(secondEvent);

        List<String> publishedOrder = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch startLatch = new CountDownLatch(1);
        Runnable claimer = () -> {
            try {
                startLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            long deadline = System.currentTimeMillis() + 10_000;
            while (System.currentTimeMillis() < deadline && publishedOrder.size() < 2) {
                Instant claimInstant = Instant.now();
                List<OutboxEntry> claimedEntries = outboxRepository.claimPending(1, claimInstant, claimInstant.minusSeconds(60));
                if (claimedEntries.isEmpty()) {
                    continue;
                }
                OutboxEntry entry = claimedEntries.getFirst();
                if (outboxRepository.existsEarlierUnpublished(entry.groupId(), entry.createdAt(), entry.id())) {
                    outboxRepository.releaseClaim(entry.eventId(), Instant.now());
                } else {
                    publishedOrder.add(entry.eventId());
                    outboxRepository.markAsPublished(entry.eventId(), Instant.now());
                }
            }
        };

        Thread firstClaimer = new Thread(claimer);
        Thread secondClaimer = new Thread(claimer);
        firstClaimer.start();
        secondClaimer.start();
        startLatch.countDown();
        firstClaimer.join(15_000);
        secondClaimer.join(15_000);

        assertThat(publishedOrder).containsExactly(firstEvent.getEventId(), secondEvent.getEventId());
    }

    private OutboxEntry findByEventId(List<OutboxEntry> entries, String eventId) {
        return entries.stream()
                .filter(entry -> entry.eventId().equals(eventId))
                .findFirst()
                .orElseThrow();
    }

    private RouteFailedEvent sampleEvent() {
        return sampleEvent("pkg-1");
    }

    private RouteFailedEvent sampleEvent(String packageId) {
        return RouteFailedEvent.builder()
                .payload(RouteFailedEvent.Payload.builder()
                        .packageId(packageId)
                        .reason("no reachable hub")
                        .build())
                .build();
    }

    @SpringBootConfiguration
    @ImportAutoConfiguration({MongoAutoConfiguration.class, DataMongoAutoConfiguration.class})
    @EnableMongoRepositories(basePackageClasses = OutboxMongoRepository.class)
    @org.springframework.context.annotation.Import({MongoOutboxRepositoryAdapter.class, MongoInboxRepositoryAdapter.class})
    static class TestConfig {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        TraceContextSupport traceContextSupport() {
            TraceContextSupport traceContextSupport = mock(TraceContextSupport.class);
            when(traceContextSupport.captureCurrent()).thenReturn(new TraceContextSupport.TraceCarrier(
                    "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
                    "rojo=00f067aa0ba902b7"
            ));
            return traceContextSupport;
        }

        @Bean
        MongoTransactionManager transactionManager(MongoDatabaseFactory databaseFactory) {
            return new MongoTransactionManager(databaseFactory);
        }

        @Bean
        TransactionTemplate transactionTemplate(MongoTransactionManager transactionManager) {
            return new TransactionTemplate(transactionManager);
        }
    }
}
