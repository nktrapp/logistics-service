package br.furb.logistics.domain.port;

public interface EventPublisherPort {

    /**
     * Publishes a message to a FIFO queue.
     *
     * @param queueName   target queue name (FIFO, ends with {@code .fifo})
     * @param messageBody serialized event envelope
     * @param groupId     SQS MessageGroupId — orders messages per aggregate (packageId)
     * @param dedupId     SQS MessageDeduplicationId — the eventId, for exactly-once enqueue
     */
    void publish(String queueName, String messageBody, String groupId, String dedupId);
}
