package br.furb.logistics.infrastructure.adapter.out.persistence.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("outbox")
public class OutboxDocument {

    @Id
    private String id;

    @Indexed(unique = true)
    private String eventId;

    private String eventType;
    private String payload;
    private String groupId;

    @Indexed
    private String status;

    @Indexed
    private Instant nextAttemptAt;

    private Integer retryCount;
    private String lastError;
    private Instant processingStartedAt;
    private Instant createdAt;
    private Instant publishedAt;
}
