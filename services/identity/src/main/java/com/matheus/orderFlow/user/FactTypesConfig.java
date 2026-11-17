package com.matheus.orderFlow.user;

import com.matheus.orderFlow.contracts.user.UserFacts;
import com.matheus.orderFlow.shared.messaging.FactTypes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class FactTypesConfig {

    @Bean
    FactTypes factTypes() {
        return new FactTypes(UserFacts.TYPES);
    }
}
