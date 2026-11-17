package com.matheus.orderFlow.contact;

import com.matheus.orderFlow.shared.web.PageResponse;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange("/internal/users")
interface UserExportClient {

    @GetExchange("/export")
    PageResponse<ExportedUser> export(@RequestParam int page, @RequestParam int size);
}
