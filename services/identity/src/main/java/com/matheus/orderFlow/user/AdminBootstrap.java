package com.matheus.orderFlow.user;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
class AdminBootstrap implements ApplicationRunner {
    static final String DEVELOPMENT_PASSWORD = "change-me-in-production";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String name;
    private final String email;
    private final String password;

    AdminBootstrap(UserRepository userRepository,
                   PasswordEncoder passwordEncoder,
                   @Value("${orderflow.security.admin.name}") String name,
                   @Value("${orderflow.security.admin.email}") String email,
                   @Value("${orderflow.security.admin.password}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.name = name;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByRole(UserRole.ADMIN)) {
            return;
        }

        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            log.warn("No administrator exists and none was configured. "
                    + "Set ADMIN_EMAIL and ADMIN_PASSWORD to create one.");
            return;
        }

        if (userRepository.existsByEmail(email)) {
            log.warn("Cannot create the administrator: {} is already registered as a regular user",
                    email);
            return;
        }

        User.validatePasswordPolicy(password);

        User administrator = User.administrator(name, email, passwordEncoder.encode(password));

        userRepository.save(administrator);

        log.info("Administrator created: {}", email);

        if (DEVELOPMENT_PASSWORD.equals(password)) {
            log.warn("The administrator was created with the development password. "
                    + "Set ADMIN_PASSWORD before running outside your machine.");
        }
    }
}
