package br.furb.logistics.application.config;

import br.furb.logistics.application.service.HubMeshService;
import br.furb.logistics.application.service.RouteCalculationService;
import br.furb.logistics.application.usecase.BuildHubConnectionsUseCase;
import br.furb.logistics.application.usecase.CalculateRouteUseCase;
import br.furb.logistics.application.usecase.GetHubUseCase;
import br.furb.logistics.application.usecase.GetRouteUseCase;
import br.furb.logistics.application.usecase.ListHubsUseCase;
import br.furb.logistics.application.usecase.RecalculateRouteUseCase;
import br.furb.logistics.application.usecase.RegisterHubConnectionUseCase;
import br.furb.logistics.application.usecase.RegisterHubUseCase;
import br.furb.logistics.application.usecase.transaction.PersistCalculatedRouteUseCase;
import br.furb.logistics.application.usecase.transaction.PersistFailedRouteUseCase;
import br.furb.logistics.application.usecase.transaction.PersistHubConnectionsUseCase;
import br.furb.logistics.application.usecase.transaction.PersistRecalculatedRouteUseCase;
import br.furb.logistics.application.usecase.transaction.PersistRegisteredHubUseCase;
import br.furb.logistics.domain.port.CepLookupPort;
import br.furb.logistics.domain.port.HubConnectionRepositoryPort;
import br.furb.logistics.domain.port.HubRepositoryPort;
import br.furb.logistics.domain.port.InboxRepositoryPort;
import br.furb.logistics.domain.port.MunicipalityGeocodingPort;
import br.furb.logistics.domain.port.OutboxRepositoryPort;
import br.furb.logistics.domain.port.RouteRepositoryPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfig {

    @Bean
    public RouteCalculationService routeCalculationService() {
        return new RouteCalculationService();
    }

    @Bean
    public HubMeshService hubMeshService() {
        return new HubMeshService();
    }

    @Bean
    public RegisterHubUseCase registerHubUseCase(CepLookupPort cepLookupPort,
                                                 MunicipalityGeocodingPort municipalityGeocodingPort,
                                                 PersistRegisteredHubUseCase persistRegisteredHubUseCase) {
        return new RegisterHubUseCase(cepLookupPort, municipalityGeocodingPort, persistRegisteredHubUseCase);
    }

    @Bean
    public PersistRegisteredHubUseCase persistRegisteredHubUseCase(HubRepositoryPort hubRepository,
                                                                   OutboxRepositoryPort outboxRepository) {
        return new PersistRegisteredHubUseCase(hubRepository, outboxRepository);
    }

    @Bean
    public BuildHubConnectionsUseCase buildHubConnectionsUseCase(HubRepositoryPort hubRepository,
                                                                 HubConnectionRepositoryPort hubConnectionRepository,
                                                                 InboxRepositoryPort inboxRepository,
                                                                 HubMeshService hubMeshService,
                                                                 PersistHubConnectionsUseCase persistHubConnectionsUseCase,
                                                                 @Value("${app.routing.auto-connect.neighbors:3}") int neighbors,
                                                                 @Value("${app.routing.auto-connect.avg-speed-kmh:60}") int averageSpeedKmh) {
        return new BuildHubConnectionsUseCase(hubRepository, hubConnectionRepository, inboxRepository,
                hubMeshService, persistHubConnectionsUseCase, neighbors, averageSpeedKmh);
    }

    @Bean
    public PersistHubConnectionsUseCase persistHubConnectionsUseCase(HubConnectionRepositoryPort hubConnectionRepository,
                                                                     OutboxRepositoryPort outboxRepository,
                                                                     InboxRepositoryPort inboxRepository) {
        return new PersistHubConnectionsUseCase(hubConnectionRepository, outboxRepository, inboxRepository);
    }

    @Bean
    public RegisterHubConnectionUseCase registerHubConnectionUseCase(HubConnectionRepositoryPort hubConnectionRepository,
                                                                     HubRepositoryPort hubRepository) {
        return new RegisterHubConnectionUseCase(hubConnectionRepository, hubRepository);
    }

    @Bean
    public CalculateRouteUseCase calculateRouteUseCase(RouteRepositoryPort routeRepository,
                                                       HubRepositoryPort hubRepository,
                                                       HubConnectionRepositoryPort hubConnectionRepository,
                                                       CepLookupPort cepLookupPort,
                                                       InboxRepositoryPort inboxRepository,
                                                       RouteCalculationService routeCalculationService,
                                                       PersistCalculatedRouteUseCase persistCalculatedRouteUseCase,
                                                       PersistFailedRouteUseCase persistFailedRouteUseCase) {
        return new CalculateRouteUseCase(routeRepository, hubRepository, hubConnectionRepository,
                cepLookupPort, inboxRepository, routeCalculationService, persistCalculatedRouteUseCase,
                persistFailedRouteUseCase);
    }

    @Bean
    public RecalculateRouteUseCase recalculateRouteUseCase(RouteRepositoryPort routeRepository,
                                                           HubRepositoryPort hubRepository,
                                                           HubConnectionRepositoryPort hubConnectionRepository,
                                                           CepLookupPort cepLookupPort,
                                                           InboxRepositoryPort inboxRepository,
                                                           RouteCalculationService routeCalculationService,
                                                           PersistRecalculatedRouteUseCase persistRecalculatedRouteUseCase,
                                                           PersistFailedRouteUseCase persistFailedRouteUseCase) {
        return new RecalculateRouteUseCase(routeRepository, hubRepository, hubConnectionRepository,
                cepLookupPort, inboxRepository, routeCalculationService, persistRecalculatedRouteUseCase,
                persistFailedRouteUseCase);
    }

    @Bean
    public PersistCalculatedRouteUseCase persistCalculatedRouteUseCase(RouteRepositoryPort routeRepository,
                                                                       OutboxRepositoryPort outboxRepository,
                                                                       InboxRepositoryPort inboxRepository) {
        return new PersistCalculatedRouteUseCase(routeRepository, outboxRepository, inboxRepository);
    }

    @Bean
    public PersistRecalculatedRouteUseCase persistRecalculatedRouteUseCase(RouteRepositoryPort routeRepository,
                                                                           OutboxRepositoryPort outboxRepository,
                                                                           InboxRepositoryPort inboxRepository) {
        return new PersistRecalculatedRouteUseCase(routeRepository, outboxRepository, inboxRepository);
    }

    @Bean
    public PersistFailedRouteUseCase persistFailedRouteUseCase(OutboxRepositoryPort outboxRepository,
                                                               InboxRepositoryPort inboxRepository) {
        return new PersistFailedRouteUseCase(outboxRepository, inboxRepository);
    }

    @Bean
    public GetRouteUseCase getRouteUseCase(RouteRepositoryPort routeRepository) {
        return new GetRouteUseCase(routeRepository);
    }

    @Bean
    public ListHubsUseCase listHubsUseCase(HubRepositoryPort hubRepository) {
        return new ListHubsUseCase(hubRepository);
    }

    @Bean
    public GetHubUseCase getHubUseCase(HubRepositoryPort hubRepository) {
        return new GetHubUseCase(hubRepository);
    }
}
