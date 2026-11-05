package com.matheus.orderFlow.cart;

import com.matheus.orderFlow.shared.security.BearerTokenPropagationInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;

@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = ServiceClientsConfig.CATALOG, types = CatalogClient.class)
@ImportHttpServices(group = ServiceClientsConfig.ORDERS, types = OrdersClient.class)
class ServiceClientsConfig {

    static final String CATALOG = "catalog";
    static final String ORDERS = "orders";

    @Bean
    RestClientHttpServiceGroupConfigurer forwardTheCallerCredential() {
        return groups -> groups.filterByName(CATALOG, ORDERS)
                .forEachClient((group, client) -> client.requestInterceptor(new BearerTokenPropagationInterceptor()));
    }
}
