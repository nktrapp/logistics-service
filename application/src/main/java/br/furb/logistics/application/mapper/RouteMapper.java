package br.furb.logistics.application.mapper;

import br.furb.logistics.application.dto.RouteResponse;
import br.furb.logistics.domain.model.Route;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface RouteMapper {

    RouteResponse toResponse(Route route);
}
