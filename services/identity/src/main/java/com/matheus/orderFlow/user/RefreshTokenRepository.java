package com.matheus.orderFlow.user;

import org.springframework.data.repository.CrudRepository;

import java.util.List;

interface RefreshTokenRepository extends CrudRepository<RefreshToken, String> {
    List<RefreshToken> findByFamilyId(String familyId);
}
