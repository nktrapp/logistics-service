package br.furb.logistics.domain.port;

public interface InboxRepositoryPort {

    boolean existsByEventId(String eventId);

    boolean saveIfAbsent(String eventId, String eventType);

    void save(String eventId, String eventType);
}
