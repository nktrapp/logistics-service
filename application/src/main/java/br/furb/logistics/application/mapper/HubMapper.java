package br.furb.logistics.application.mapper;

import br.furb.logistics.application.dto.HubResponse;
import br.furb.logistics.domain.model.Hub;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface HubMapper {

    HubMapper INSTANCE = Mappers.getMapper(HubMapper.class);

    HubResponse toResponse(Hub hub);
}
