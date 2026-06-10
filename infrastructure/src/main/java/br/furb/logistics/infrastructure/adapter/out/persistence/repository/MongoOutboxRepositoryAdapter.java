package br.furb.logistics.infrastructure.adapter.out.persistence.repository;

import br.furb.logistics.domain.event.DomainEvent;
import br.furb.logistics.domain.port.OutboxRepositoryPort;
import br.furb.logistics.infrastructure.adapter.out.persistence.document.OutboxDocument;
import br.furb.logistics.infrastructure.adapter.out.persistence.repository.mongo.OutboxMongoRepository;
import br.furb.logistics.infrastructure.config.TraceContextSupport;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class MongoOutboxRepositoryAdapter implements OutboxRepositoryPort {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_FAILED = "FAILED";
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;

    private final OutboxMongoRepository mongoRepository;
    private final ObjectMapper objectMapper;
    private final MongoTemplate mongoTemplate;
    private final TraceContextSupport traceContextSupport;

    @Override
    public void save(DomainEvent event) {
        try {
            Instant createdAt = Instant.now();
            String payload = objectMapper.writeValueAsString(event.getPayload());
            String groupId = event.getPartitionKey() != null ? event.getPartitionKey() : event.getEventId();
            TraceContextSupport.TraceCarrier traceCarrier = traceContextSupport.captureCurrent();

            OutboxDocument document = OutboxDocument.builder()
                    .eventId(event.getEventId())
                    .eventType(event.getEventType())
                    .payload(payload)
                    .groupId(groupId)
                    .traceparent(traceCarrier.traceparent())
                    .tracestate(traceCarrier.tracestate())
                    .status(STATUS_PENDING)
                    .nextAttemptAt(createdAt)
                    .retryCount(0)
                    .createdAt(createdAt)
                    .build();

            mongoRepository.save(document);
        } catch (JacksonException e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }
    }

    @Override
    public List<OutboxEntry> claimPending(int batchSize, Instant claimedAt, Instant retryTimedOutBefore) {
        List<OutboxEntry> claimedEvents = new ArrayList<>();

        for (int index = 0; index < batchSize; index++) {
            Optional<OutboxEntry> claimedEvent = claimNext(claimedAt, retryTimedOutBefore);
            if (claimedEvent.isEmpty()) {
                return claimedEvents;
            }
            claimedEvents.add(claimedEvent.get());
        }

        return claimedEvents;
    }

    @Override
    public void markAsPublished(String eventId, Instant publishedAt) {
        Query query = Query.query(Criteria.where("eventId").is(eventId));
        Update update = new Update()
                .set("status", STATUS_PUBLISHED)
                .set("publishedAt", publishedAt)
                .unset("processingStartedAt")
                .unset("lastError");

        mongoTemplate.updateFirst(query, update, OutboxDocument.class);
    }

    @Override
    public RetryOutcome markForRetry(String eventId, String errorMessage, Instant nextAttemptAt, int maxAttempts) {
        Query query = Query.query(Criteria.where("eventId").is(eventId));
        Update update = new Update()
                .inc("retryCount", 1)
                .set("lastError", sanitizeErrorMessage(errorMessage))
                .unset("processingStartedAt");

        OutboxDocument updatedDocument = mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                OutboxDocument.class
        );

        if (updatedDocument == null) {
            return new RetryOutcome(false, 0);
        }

        Integer retryCount = updatedDocument.getRetryCount();
        if (retryCount != null && retryCount >= maxAttempts) {
            Update failUpdate = new Update()
                    .set("status", STATUS_FAILED)
                    .unset("nextAttemptAt");
            mongoTemplate.updateFirst(query, failUpdate, OutboxDocument.class);
            return new RetryOutcome(false, retryCount);
        }

        Update retryUpdate = new Update()
                .set("status", STATUS_PENDING)
                .set("nextAttemptAt", nextAttemptAt);
        mongoTemplate.updateFirst(query, retryUpdate, OutboxDocument.class);
        return new RetryOutcome(true, retryCount == null ? 0 : retryCount);
    }

    @Override
    public long deletePublishedBefore(Instant threshold) {
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("status").is(STATUS_PUBLISHED),
                Criteria.where("publishedAt").lt(threshold)
        ));
        return mongoTemplate.remove(query, OutboxDocument.class).getDeletedCount();
    }

    @Override
    public long countFailed() {
        return mongoTemplate.count(Query.query(Criteria.where("status").is(STATUS_FAILED)), OutboxDocument.class);
    }

    @Override
    public boolean existsEarlierUnpublished(String groupId, Instant createdAt, String outboxId) {
        Criteria earlierByTime = Criteria.where("createdAt").lt(createdAt);
        Criteria sameTimeSmallerId = new Criteria().andOperator(
                Criteria.where("createdAt").is(createdAt),
                Criteria.where("_id").lt(new ObjectId(outboxId)));
        Criteria blockerCriteria = new Criteria().andOperator(
                Criteria.where("groupId").is(groupId),
                Criteria.where("status").in(STATUS_PENDING, STATUS_IN_PROGRESS, STATUS_FAILED),
                new Criteria().orOperator(earlierByTime, sameTimeSmallerId));
        return mongoTemplate.exists(new Query(blockerCriteria), OutboxDocument.class);
    }

    @Override
    public void releaseClaim(String eventId, Instant nextAttemptAt) {
        Query query = Query.query(Criteria.where("eventId").is(eventId).and("status").is(STATUS_IN_PROGRESS));
        Update update = new Update()
                .set("status", STATUS_PENDING)
                .set("nextAttemptAt", nextAttemptAt)
                .unset("processingStartedAt");
        mongoTemplate.updateFirst(query, update, OutboxDocument.class);
    }

    private Optional<OutboxEntry> claimNext(Instant claimedAt, Instant retryTimedOutBefore) {
        Criteria pendingCriteria = new Criteria().andOperator(
                Criteria.where("status").is(STATUS_PENDING),
                new Criteria().orOperator(
                        Criteria.where("nextAttemptAt").lte(claimedAt),
                        Criteria.where("nextAttemptAt").is(null)
                )
        );

        Criteria timedOutCriteria = new Criteria().andOperator(
                Criteria.where("status").is(STATUS_IN_PROGRESS),
                Criteria.where("processingStartedAt").lt(retryTimedOutBefore)
        );

        Query query = new Query(new Criteria().orOperator(pendingCriteria, timedOutCriteria))
                .with(Sort.by(Sort.Direction.ASC, "nextAttemptAt", "createdAt"));

        Update update = new Update()
                .set("status", STATUS_IN_PROGRESS)
                .set("processingStartedAt", claimedAt);

        OutboxDocument claimedDocument = mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                OutboxDocument.class
        );

        if (claimedDocument == null) {
            return Optional.empty();
        }

        int retryCount = claimedDocument.getRetryCount() == null ? 0 : claimedDocument.getRetryCount();
        return Optional.of(new OutboxEntry(
                claimedDocument.getId(),
                claimedDocument.getEventId(),
                claimedDocument.getEventType(),
                claimedDocument.getPayload(),
                claimedDocument.getGroupId(),
                claimedDocument.getTraceparent(),
                claimedDocument.getTracestate(),
                claimedDocument.getCreatedAt(),
                retryCount
        ));
    }

    private String sanitizeErrorMessage(String errorMessage) {
        if (errorMessage == null || errorMessage.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return errorMessage;
        }
        return errorMessage.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }
}
