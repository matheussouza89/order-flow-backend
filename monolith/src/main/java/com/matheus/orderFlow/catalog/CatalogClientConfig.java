package com.matheus.orderFlow.catalog;

import com.matheus.orderFlow.shared.security.BearerTokenPropagationInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;

@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = CatalogClientConfig.CATALOG, types = CatalogClient.class)
class CatalogClientConfig {

    static final String CATALOG = "catalog";

    @Bean
    RestClientHttpServiceGroupConfigurer forwardTheCallerCredentialToTheCatalog() {
        return groups -> groups.filterByName(CATALOG)
                .forEachClient((group, client) -> client.requestInterceptor(new BearerTokenPropagationInterceptor()));
    }
}
