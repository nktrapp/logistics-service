package br.furb.logistics.core.mapper;

import br.furb.logistics.core.dto.RouteResponse;
import br.furb.logistics.domain.model.Route;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface RouteMapper {

    RouteMapper INSTANCE = Mappers.getMapper(RouteMapper.class);

    RouteResponse toResponse(Route route);
}
