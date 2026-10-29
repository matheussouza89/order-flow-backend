package com.matheus.orderFlow.order;

import com.matheus.orderFlow.contracts.order.OrderFacts;
import com.matheus.orderFlow.shared.messaging.FactTypes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class FactTypesConfig {

    @Bean
    FactTypes factTypes() {
        return new FactTypes(OrderFacts.TYPES, OrderFacts.LEGACY_TYPES);
    }
}
