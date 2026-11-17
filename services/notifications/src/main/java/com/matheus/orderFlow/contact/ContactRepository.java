package com.matheus.orderFlow.contact;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface ContactRepository extends JpaRepository<Contact, UUID> {
}
