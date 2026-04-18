package com.example.gamematch.common;

import com.example.gamematch.common.exception.ConflictException;
import com.example.gamematch.common.exception.QueueOperationException;
import com.example.gamematch.common.exception.ResourceNotFoundException;
import com.example.gamematch.common.exception.ValidationException;
import com.example.gamematch.observability.MatchmakingMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

    private MatchmakingMetrics metrics;
    private ApiExceptionHandler handler;

    @BeforeEach
    void setUp() {
        metrics = new MatchmakingMetrics(new SimpleMeterRegistry());
        handler = new ApiExceptionHandler(metrics);
    }

    @Test
    @DisplayName("Handles ResourceNotFoundException with 404 ProblemDetail")
    void handleNotFound() {
        ProblemDetail problem = handler.handleNotFound(new ResourceNotFoundException("Not found"));
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getDetail()).isEqualTo("Not found");
    }

    @Test
    @DisplayName("Handles ConflictException with 409 ProblemDetail")
    void handleConflict() {
        ProblemDetail problem = handler.handleConflict(new ConflictException("Conflict"));
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getDetail()).isEqualTo("Conflict");
    }

    @Test
    @DisplayName("Handles ValidationException with 400 ProblemDetail")
    void handleValidation() {
        ProblemDetail problem = handler.handleValidation(new ValidationException("Invalid field"));
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getDetail()).isEqualTo("Invalid field");
    }

    @Test
    @DisplayName("Handles QueueOperationException with 400 ProblemDetail")
    void handleQueueOperation() {
        ProblemDetail problem = handler.handleQueueOperation(new QueueOperationException("Queue failed"));
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getDetail()).isEqualTo("Queue failed");
    }

    @Test
    @DisplayName("Handles general Exception with 500 ProblemDetail")
    void handleGeneral() {
        ProblemDetail problem = handler.handleGeneralException(new RuntimeException("Crash"));
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
    }
}
