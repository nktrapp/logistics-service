package br.furb.logistics.infrastructure.adapter.out.messaging;

import io.awspring.cloud.sqs.listener.SqsHeaders;
import io.awspring.cloud.sqs.operations.SqsSendOptions;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Consumer;

import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("SqsEventPublisherAdapter")
class SqsEventPublisherAdapterTest {

    @Mock
    SqsTemplate sqsTemplate;

    @Test
    @DisplayName("Given an event, should send it to the queue with the FIFO group and deduplication headers")
    void shouldSendWithFifoHeaders() {
        SqsEventPublisherAdapter adapter = new SqsEventPublisherAdapter(sqsTemplate);

        adapter.publish("logistics-events-queue.fifo", "message-body", "group-1", "dedup-1");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<SqsSendOptions<Object>>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(sqsTemplate).send(captor.capture());

        @SuppressWarnings("unchecked")
        SqsSendOptions<Object> options = mock(SqsSendOptions.class, RETURNS_SELF);
        captor.getValue().accept(options);

        verify(options).queue("logistics-events-queue.fifo");
        verify(options).payload("message-body");
        verify(options).header(SqsHeaders.MessageSystemAttributes.SQS_MESSAGE_GROUP_ID_HEADER, "group-1");
        verify(options).header(SqsHeaders.MessageSystemAttributes.SQS_MESSAGE_DEDUPLICATION_ID_HEADER, "dedup-1");
    }
}
