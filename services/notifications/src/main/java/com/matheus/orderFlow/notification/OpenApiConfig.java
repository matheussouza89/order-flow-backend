package com.matheus.orderFlow.notification;

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
                title = "OrderFlow Notifications API",
                version = "1.0.0",
                contact = @Contact(name = "Matheus Souza"),
                description = """
                        ## Purpose
                        The emails sent to the customer when an order is confirmed, and what became
                        of each one. Every route is for administrators.

                        ## Where a notification comes from
                        This service learns about an order from the facts the orders service
                        publishes, never from a call, and about the customer's email from the facts
                        the identity service publishes. If it is down, orders keep working, and the
                        emails go out when it comes back. The same fact arriving twice sends one
                        email.

                        The customers registered before this service existed are copied once, when
                        it starts operating, by an administrator calling
                        `POST /admin/notifications/contacts/import`.

                        ## Attempts
                        A notification starts as `PENDING` and is sent within a few seconds. When
                        the mail server fails, or the customer's email is not known yet, it waits
                        longer after each attempt: 30 seconds, then 1, 2 and 4 minutes. It ends as:
                        - `SENT` — the mail server accepted the email
                        - `FAILED` — the mail server refused it five times
                        - `NO_RECIPIENT` — no email was known for the customer after five attempts

                        ## Errors
                        Every error response shares the same body (`ErrorResponse`), with
                        `status`, `message` and `errors`. **401** means no token or an invalid one;
                        **403**, a token without the administrator role.
                        """
        )
)
class OpenApiConfig {
}
