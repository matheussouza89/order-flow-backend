package com.matheus.orderFlow.sales;

import com.matheus.orderFlow.shared.security.BearerTokenPropagationInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;

@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = OrdersClientConfig.ORDERS, types = OrderExportClient.class)
class OrdersClientConfig {

    static final String ORDERS = "orders";

    @Bean
    RestClientHttpServiceGroupConfigurer forwardTheCallerCredentialToOrders() {
        return groups -> groups.filterByName(ORDERS)
                .forEachClient((group, client) -> client.requestInterceptor(new BearerTokenPropagationInterceptor()));
    }
}
