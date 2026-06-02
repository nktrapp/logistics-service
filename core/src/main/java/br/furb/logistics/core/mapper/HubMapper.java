package br.furb.logistics.core.mapper;

import br.furb.logistics.core.dto.HubResponse;
import br.furb.logistics.domain.model.Hub;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface HubMapper {

    HubMapper INSTANCE = Mappers.getMapper(HubMapper.class);

    HubResponse toResponse(Hub hub);
}
