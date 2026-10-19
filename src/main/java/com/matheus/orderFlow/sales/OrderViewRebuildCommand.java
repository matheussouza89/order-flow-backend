package com.matheus.orderFlow.sales;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "orderflow.views.rebuild", havingValue = "true")
class OrderViewRebuildCommand implements ApplicationRunner {

    private final OrderViewRebuilder rebuilder;

    @Override
    public void run(ApplicationArguments args) {
        rebuilder.rebuild();
    }
}
