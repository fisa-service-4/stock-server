package com.stock.global.exception;

import com.stock.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(GlobalException.class)
  public ResponseEntity<ApiResponse<Void>> handleGlobalException(
      GlobalException e, HttpServletRequest request) {
    String traceId = MDC.get("traceId");
    ErrorCode errorCode = e.getErrorCode();
    return ResponseEntity.status(errorCode.getHttpStatus())
        .body(ApiResponse.error(errorCode.getCode(), errorCode.getMessage(), traceId));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidationException(
      MethodArgumentNotValidException e, HttpServletRequest request) {
    String traceId = MDC.get("traceId");
    String message =
        e.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .findFirst()
            .orElse(ErrorCode.VALID_001.getMessage());
    return ResponseEntity.badRequest()
        .body(ApiResponse.error(ErrorCode.VALID_001.getCode(), message, traceId));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleException(
      Exception e, HttpServletRequest request) {
    String traceId = MDC.get("traceId");
    log.error(
        "[{}] 처리되지 않은 예외 발생 uri={} message={}",
        traceId,
        request.getRequestURI(),
        e.getMessage(),
        e);
    return ResponseEntity.internalServerError()
        .body(ApiResponse.error("INTERNAL_ERROR", e.getMessage(), traceId));
  }
}
