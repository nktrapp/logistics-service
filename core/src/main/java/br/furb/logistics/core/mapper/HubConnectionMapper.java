package br.furb.logistics.core.mapper;

import br.furb.logistics.core.dto.RegisterConnectionCommand;
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
