package br.furb.logistics.domain.port;

import br.furb.logistics.domain.model.Hub;

import java.util.List;
import java.util.Optional;

public interface HubRepository {

    Hub save(Hub hub);

    Optional<Hub> findById(String id);

    List<Hub> findByCity(String city);

    List<Hub> findByCityAndState(String city, String state);

    List<Hub> findAll();

    List<Hub> findAllActive();
}
