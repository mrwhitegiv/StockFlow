package com.stockflow.exception;

import com.stockflow.common.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiResponse<Void>> businessError(ApiException exception) {
        return error(exception.getStatus().value(), exception.getMessage());
    }

    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<ApiResponse<Void>> duplicate(DuplicateKeyException exception) {
        return error(409, "编码或名称已存在，请使用其他值");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiResponse<Void>> integrity(DataIntegrityViolationException exception) {
        return error(409, "数据约束冲突：记录被引用或关联数据已变化，请刷新后重试");
    }

    @ExceptionHandler(DataAccessResourceFailureException.class)
    ResponseEntity<ApiResponse<Void>> databaseUnavailable(DataAccessResourceFailureException exception) {
        log.warn("Database unavailable", exception);
        return error(503, "Database unavailable");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> invalidBody(MethodArgumentNotValidException exception) {
        String fields = exception.getBindingResult().getFieldErrors().stream()
                .map(field -> field.getField()).distinct().sorted().reduce((a, b) -> a + ", " + b).orElse("body");
        return error(400, "请求字段不合法：" + fields);
    }

    @ExceptionHandler({HandlerMethodValidationException.class, ConstraintViolationException.class,
            MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ApiResponse<Void>> invalidRequest(Exception exception) {
        return error(400, "请求格式或参数不合法，请检查 ID、分页范围和 JSON 字段");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> unexpectedError(Exception exception) {
        if (exception instanceof ErrorResponse response && response.getStatusCode().is4xxClientError()) {
            int status = response.getStatusCode().value();
            return error(status, switch (status) {
                case 404 -> "接口不存在";
                case 405 -> "不支持此请求方法";
                case 415 -> "请使用 application/json";
                default -> "请求不合法";
            });
        }
        log.error("Unhandled request error", exception);
        return error(500, "Internal server error");
    }

    private ResponseEntity<ApiResponse<Void>> error(int status, String message) {
        return ResponseEntity.status(status).body(ApiResponse.error(status, message));
    }
}
