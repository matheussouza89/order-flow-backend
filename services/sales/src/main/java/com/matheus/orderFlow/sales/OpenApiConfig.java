package com.matheus.orderFlow.sales;

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
                title = "OrderFlow Sales API",
                version = "1.0.0",
                contact = @Contact(name = "Matheus Souza"),
                description = """
                        ## Purpose
                        Sales reports for administrators: totals per day and the best-selling
                        products. Every route requires the `ADMIN` role.

                        ## Where the numbers come from
                        The reports read a view built from the facts the orders service
                        publishes, never from the orders database. The view is updated
                        asynchronously: an order confirmed a moment ago may take a few seconds to
                        appear, and a status change that arrives out of order may take up to
                        about 90 seconds. If this service is down, orders are still confirmed
                        normally, and the view catches up when it comes back.

                        Daily totals are grouped by the day of confirmation in America/Sao_Paulo,
                        include both dates, and show zero for days without sales. Cancelled orders
                        are left out, and a period is limited to 366 days. The ranking counts units
                        sold, not revenue, and `limit` accepts from 1 to 100.

                        ## Rebuilding the view
                        `POST /admin/sales/rebuild` reads every confirmed order from the orders
                        service and writes the view again, using the caller's credential. It
                        never deletes first and never replaces an order with older data, so it can
                        run at any time and be repeated. If the orders service does not answer,
                        it responds **503** and nothing is lost.

                        ## Errors
                        Every error response shares the same body (`ErrorResponse`), with
                        `status`, `message` and `errors`. **401** means no token or an invalid one;
                        **403** means a valid token without the `ADMIN` role.
                        """
        )
)
public class OpenApiConfig {
}
