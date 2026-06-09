package br.furb.logistics.application.mapper;

import br.furb.logistics.application.dto.RegisterConnectionCommand;
import br.furb.logistics.domain.model.HubConnection;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface HubConnectionMapper {

    @Mapping(target = "id", ignore = true)
    HubConnection toDomain(RegisterConnectionCommand command);
}
