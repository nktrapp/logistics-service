package br.furb.logistics.domain.port;

public interface EventPublisherPort {

    void publish(String queueName, String messageBody, String groupId, String dedupId);
}
