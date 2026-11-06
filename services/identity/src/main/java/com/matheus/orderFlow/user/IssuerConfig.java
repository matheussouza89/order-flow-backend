package com.matheus.orderFlow.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
class IssuerConfig {

    @Bean
    PasswordEncoder passwordEncoder(
            @Value("${orderflow.security.bcrypt-strength:12}") int strength) {
        return new BCryptPasswordEncoder(strength);
    }
}
