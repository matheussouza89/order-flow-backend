package com.matheus.orderFlow.contact;

import com.matheus.orderFlow.shared.security.BearerTokenPropagationInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;

@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = IdentityClientConfig.IDENTITY, types = UserExportClient.class)
class IdentityClientConfig {
    static final String IDENTITY = "identity";

    @Bean
    RestClientHttpServiceGroupConfigurer forwardTheCallerCredentialToIdentity() {
        return groups -> groups.filterByName(IDENTITY)
                .forEachClient((group, client) -> client.requestInterceptor(new BearerTokenPropagationInterceptor()));
    }
}
