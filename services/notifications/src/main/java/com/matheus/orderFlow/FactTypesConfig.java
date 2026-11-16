package com.matheus.orderFlow;

import com.matheus.orderFlow.contracts.order.OrderFacts;
import com.matheus.orderFlow.contracts.user.UserFacts;
import com.matheus.orderFlow.shared.messaging.FactTypes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
class FactTypesConfig {

    @Bean
    FactTypes factTypes() {
        Map<String, Class<?>> types = new HashMap<>(OrderFacts.TYPES);
        types.putAll(UserFacts.TYPES);
        return new FactTypes(types);
    }
}
