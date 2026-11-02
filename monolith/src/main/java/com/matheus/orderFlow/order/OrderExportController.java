package com.matheus.orderFlow.order;

import com.matheus.orderFlow.shared.web.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/orders")
@RequiredArgsConstructor
class OrderExportController {
    private final OrderService orderService;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/export")
    public PageResponse<OrderExportResponse> export(@RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "100") int size) {
        return orderService.exportConfirmed(page, size);
    }
}
