package br.furb.logistics.domain.port;

public interface InboxRepository {

    boolean existsByEventId(String eventId);

    boolean saveIfAbsent(String eventId, String eventType);

    void save(String eventId, String eventType);
}
