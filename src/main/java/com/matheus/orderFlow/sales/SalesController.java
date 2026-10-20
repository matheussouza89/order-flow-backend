package com.matheus.orderFlow.sales;

import com.matheus.orderFlow.shared.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Tag(
        name = "Sales",
        description = "Sales reports for administrators"
)
@RestController
@RequestMapping("/admin/sales")
@RequiredArgsConstructor
class SalesController {
    private final SalesService salesService;

    @Operation(summary = "Daily sales",
            description = "Sums the totals of non-cancelled orders by the day of confirmation, in "
                    + "America/Sao_Paulo. Both dates are included, and days without sales appear with zero.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Total per day across the period"),
            @ApiResponse(responseCode = "400", description = "Start after end, period longer than 366 days, "
                    + "or a missing or malformed date",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Only administrators can do this",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/daily")
    public List<DailySalesResponse> dailySales(@RequestParam LocalDate from, @RequestParam LocalDate to) {
        return salesService.dailySales(from, to);
    }

    @Operation(summary = "Top products",
            description = "Ranks products by quantity sold in non-cancelled orders, most sold first.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ranking, limited to the requested size"),
            @ApiResponse(responseCode = "400", description = "Limit outside 1 to 100",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Only administrators can do this",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/top-products")
    public List<TopProductResponse> topProducts(@RequestParam(defaultValue = "10") int limit) {
        return salesService.topProducts(limit);
    }
}
