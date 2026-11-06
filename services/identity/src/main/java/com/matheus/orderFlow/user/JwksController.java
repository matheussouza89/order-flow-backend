package com.matheus.orderFlow.user;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
class JwksController {
    private final RSAKey signingKey;

    @GetMapping("/.well-known/jwks.json")
    Map<String, Object> publicKeys() {
        return new JWKSet(signingKey).toPublicJWKSet().toJSONObject();
    }
}
