package com.matheus.orderFlow.shared.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    @Test
    void shouldAnswerConflictWhenOrderWasModifiedConcurrently() {
        var handler = new GlobalExceptionHandler();

        var response = handler.handleConcurrentModification(
                new OptimisticLockingFailureException("stale version"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
