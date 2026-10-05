package com.matheus.orderFlow.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface UserRepository extends JpaRepository<User, UUID> {
    boolean existsByEmail(String email);
}
