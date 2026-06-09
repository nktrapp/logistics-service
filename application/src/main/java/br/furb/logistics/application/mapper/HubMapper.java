package br.furb.logistics.application.mapper;

import br.furb.logistics.application.dto.HubResponse;
import br.furb.logistics.domain.model.Hub;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface HubMapper {

    HubResponse toResponse(Hub hub);
}
