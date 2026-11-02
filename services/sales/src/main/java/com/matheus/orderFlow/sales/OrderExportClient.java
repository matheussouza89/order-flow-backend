package com.matheus.orderFlow.sales;

import com.matheus.orderFlow.shared.web.PageResponse;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange("/internal/orders")
interface OrderExportClient {

    @GetExchange("/export")
    PageResponse<ExportedOrder> export(@RequestParam int page, @RequestParam int size);
}
