package com.matheus.orderFlow.user;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Paste the token returned by POST /auth/login."
)
@OpenAPIDefinition(
        security = @SecurityRequirement(name = "bearerAuth"),
        info = @Info(
                title = "OrderFlow Identity API",
                version = "1.0.0",
                contact = @Contact(name = "Matheus Souza"),
                description = """
                        ## Purpose
                        Accounts and credentials: registering, signing in, renewing and revoking
                        tokens. This is the only service that issues tokens; every other one
                        only verifies them.

                        ## Accounts
                        `POST /auth/register` creates an account and returns **201** with the
                        new user's URI in `Location`.

                        The role is decided by the system and cannot be sent by the client, so
                        registering never produces an administrator.

                        Passwords are stored only as a BCrypt hash and never appear in any
                        response. A password must have at least 8 characters, checked before
                        hashing.

                        ## Authentication
                        `POST /auth/login` returns two tokens, because they answer opposite
                        problems:

                        - **access** — a JWT valid for 15 minutes, sent on every request as
                          `Authorization: Bearer <token>`. In Swagger UI, use the **Authorize**
                          button.
                        - **refresh** — an opaque string valid for 7 days, used only against
                          `POST /auth/refresh` to obtain a new pair.

                        A signed token cannot be cancelled, so a long one would mean a long
                        window for whoever steals it. The access token is therefore short, and
                        the refresh token — which is stored, and can be deleted — is what makes
                        revoking possible. Revoking takes effect within one access-token
                        lifetime.

                        Every refresh rotates: the token sent stops working and a new one comes
                        back. If an already-used refresh token shows up again, two copies of it
                        exist, which suggests theft — so **every token of that login is
                        revoked**, not only the repeated one. Both the thief and the legitimate
                        holder must sign in again, and only the latter knows the password.

                        Each login starts its own family, so `POST /auth/logout` on one device
                        does not disconnect the others. The access token already issued stays
                        valid until it expires.

                        Registering, logging in and refreshing need no token. Everything else
                        does.

                        The access token is signed with RS256. The other services verify it
                        with the public keys this service publishes on the internal network,
                        so none of them can issue a token.

                        ## Who owns what
                        `/users/{id}` answers **403** for someone else's account, not 404: the
                        caller already knows that identifier — it comes from the `Location` of
                        their own registration and is the subject of their own token — so there
                        is no existence left to hide. An administrator reaches every account.

                        A login failure always answers the same way, whether the email is unknown
                        or the password is wrong, so the endpoint cannot be used to find out who
                        has an account.

                        The access token carries only the user id and the role. Its payload is
                        encoded, not encrypted — anyone holding the token can read it — so no
                        personal data is placed in it.

                        The role is read from the account on every refresh rather than copied
                        from the stored token, so demoting an administrator takes effect within
                        one access-token lifetime instead of waiting for the refresh to expire.

                        ## Errors
                        Every error response shares the same body (`ErrorResponse`), with
                        `status`, `message` and `errors`. The `errors` map is filled only when
                        there is a request field to blame.
                        """
        )
)
class OpenApiConfig {
}
