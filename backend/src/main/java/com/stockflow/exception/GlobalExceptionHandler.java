package com.stockflow.exception;

import com.stockflow.common.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiResponse<Void>> databaseUnavailable(DataAccessException exception) {
        log.warn("Database connectivity check failed", exception);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(503, "Database unavailable"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> invalidRequest(MethodArgumentNotValidException exception) {
        return ResponseEntity.badRequest().body(ApiResponse.error(400, "Validation failed"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> unexpectedError(Exception exception) {
        log.error("Unhandled request error", exception);
        return ResponseEntity.internalServerError().body(ApiResponse.error(500, "Internal server error"));
    }
}
