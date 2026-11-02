package com.matheus.orderFlow.sales;

import com.matheus.orderFlow.shared.exception.ServiceUnavailableException;
import com.matheus.orderFlow.shared.web.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

@Slf4j
@Service
@RequiredArgsConstructor
class SalesRebuildService {

    private static final int PAGE_SIZE = 100;

    private final OrderExportClient orders;
    private final OrderViewStore store;

    int rebuild() {
        int rebuilt = 0;
        int next = 0;
        PageResponse<ExportedOrder> page;

        do {
            page = exportPage(next++);
            rebuilt += (int) page.content().stream()
                    .map(OrderView::exported)
                    .filter(store::replaceIfNewer)
                    .count();
        } while (next < page.totalPages());

        log.info("Order views rebuilt from the orders export: {} documents", rebuilt);
        return rebuilt;
    }

    private PageResponse<ExportedOrder> exportPage(int number) {
        try {
            return orders.export(number, PAGE_SIZE);
        } catch (ResourceAccessException | HttpServerErrorException unavailable) {
            throw new ServiceUnavailableException(OrdersClientConfig.ORDERS, unavailable);
        }
    }
}
