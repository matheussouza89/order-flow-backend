package com.matheus.orderFlow;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class OrderFlowApplicationIT {

	@Test
	void contextLoads() {
	}

}
