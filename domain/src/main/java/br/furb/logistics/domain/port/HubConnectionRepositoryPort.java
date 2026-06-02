package br.furb.logistics.domain.port;

import br.furb.logistics.domain.model.HubConnection;

import java.util.List;

public interface HubConnectionRepositoryPort {

    HubConnection save(HubConnection connection);

    List<HubConnection> findByOriginHubId(String originHubId);

    List<HubConnection> findAll();
}
