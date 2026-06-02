package br.furb.logistics.infrastructure.config;

import br.furb.logistics.core.service.RouteCalculationService;
import br.furb.logistics.core.usecase.CalculateRouteUseCase;
import br.furb.logistics.core.usecase.GetHubUseCase;
import br.furb.logistics.core.usecase.GetRouteUseCase;
import br.furb.logistics.core.usecase.ListHubsUseCase;
import br.furb.logistics.core.usecase.RecalculateRouteUseCase;
import br.furb.logistics.core.usecase.RegisterHubConnectionUseCase;
import br.furb.logistics.core.usecase.RegisterHubUseCase;
import br.furb.logistics.domain.port.CepLookupPort;
import br.furb.logistics.domain.port.HubConnectionRepository;
import br.furb.logistics.domain.port.HubRepository;
import br.furb.logistics.domain.port.InboxRepository;
import br.furb.logistics.domain.port.OutboxRepository;
import br.furb.logistics.domain.port.RouteRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfig {

    @Bean
    public RouteCalculationService routeCalculationService() {
        return new RouteCalculationService();
    }

    @Bean
    public RegisterHubUseCase registerHubUseCase(HubRepository hubRepository, CepLookupPort cepLookupPort) {
        return new RegisterHubUseCase(hubRepository, cepLookupPort);
    }

    @Bean
    public RegisterHubConnectionUseCase registerHubConnectionUseCase(HubConnectionRepository hubConnectionRepository, HubRepository hubRepository) {
        return new RegisterHubConnectionUseCase(hubConnectionRepository, hubRepository);
    }

    @Bean
    public CalculateRouteUseCase calculateRouteUseCase(RouteRepository routeRepository,
                                                       HubRepository hubRepository,
                                                       HubConnectionRepository hubConnectionRepository,
                                                       CepLookupPort cepLookupPort,
                                                       OutboxRepository outboxRepository,
                                                       InboxRepository inboxRepository,
                                                       RouteCalculationService routeCalculationService) {
        return new CalculateRouteUseCase(routeRepository, hubRepository, hubConnectionRepository,
                cepLookupPort, outboxRepository, inboxRepository, routeCalculationService);
    }

    @Bean
    public RecalculateRouteUseCase recalculateRouteUseCase(RouteRepository routeRepository,
                                                           HubRepository hubRepository,
                                                           HubConnectionRepository hubConnectionRepository,
                                                           CepLookupPort cepLookupPort,
                                                           OutboxRepository outboxRepository,
                                                           InboxRepository inboxRepository,
                                                           RouteCalculationService routeCalculationService) {
        return new RecalculateRouteUseCase(routeRepository, hubRepository, hubConnectionRepository,
                cepLookupPort, outboxRepository, inboxRepository, routeCalculationService);
    }

    @Bean
    public GetRouteUseCase getRouteUseCase(RouteRepository routeRepository) {
        return new GetRouteUseCase(routeRepository);
    }

    @Bean
    public ListHubsUseCase listHubsUseCase(HubRepository hubRepository) {
        return new ListHubsUseCase(hubRepository);
    }

    @Bean
    public GetHubUseCase getHubUseCase(HubRepository hubRepository) {
        return new GetHubUseCase(hubRepository);
    }
}
