package com.matheus.orderFlow.contact;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContactService {
    private final ContactRepository contactRepository;

    @Transactional(readOnly = true)
    public Optional<String> emailOf(UUID userId) {
        return contactRepository.findById(userId).map(Contact::getEmail);
    }

    @Transactional
    public boolean record(UUID userId, String email, Instant updatedAt) {
        return contactRepository.findById(userId)
                .map(contact -> contact.replaceIfNewer(email, updatedAt))
                .orElseGet(() -> {
                    contactRepository.save(new Contact(userId, email, updatedAt));
                    return true;
                });
    }
}
