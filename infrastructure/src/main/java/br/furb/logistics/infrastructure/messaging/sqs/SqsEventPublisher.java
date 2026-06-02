package br.furb.logistics.infrastructure.messaging.sqs;

import br.furb.logistics.core.port.EventPublisherPort;
import io.awspring.cloud.sqs.listener.SqsHeaders;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SqsEventPublisher implements EventPublisherPort {

    private final SqsTemplate sqsTemplate;

    @Override
    public void publish(String queueName, String messageBody, String groupId, String dedupId) {
        log.info("[sqs-publisher] Publishing message to queue {} (group {})", queueName, groupId);
        sqsTemplate.send(to -> to
                .queue(queueName)
                .payload(messageBody)
                .header(SqsHeaders.MessageSystemAttributes.SQS_MESSAGE_GROUP_ID_HEADER, groupId)
                .header(SqsHeaders.MessageSystemAttributes.SQS_MESSAGE_DEDUPLICATION_ID_HEADER, dedupId));
    }
}
