package br.furb.logistics.application.mapper;

import br.furb.logistics.application.dto.RegisterConnectionCommand;
import br.furb.logistics.domain.model.HubConnection;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface HubConnectionMapper {

    HubConnectionMapper INSTANCE = Mappers.getMapper(HubConnectionMapper.class);

    @Mapping(target = "id", ignore = true)
    HubConnection toDomain(RegisterConnectionCommand command);
}
