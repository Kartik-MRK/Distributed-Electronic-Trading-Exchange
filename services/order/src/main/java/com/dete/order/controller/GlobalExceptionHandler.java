package com.dete.order.controller;

import com.dete.order.dto.ErrorResponse;
import com.dete.order.exception.AccountServiceUnavailableException;
import com.dete.order.exception.InsufficientFundsException;
import com.dete.order.exception.InvalidOrderException;
import com.dete.order.exception.OrderNotCancellableException;
import com.dete.order.exception.OrderNotFoundException;
import com.dete.order.exception.PreTradeRiskException;
import com.dete.order.exception.RiskServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidationException(
      MethodArgumentNotValidException ex) {
    String errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining(", "));
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), "Validation Failed", errors));
  }

  @ExceptionHandler(RiskServiceUnavailableException.class)
  public ResponseEntity<ErrorResponse> handleRiskUnavailable(RiskServiceUnavailableException ex) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(
            ErrorResponse.of(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                "RISK_SERVICE_UNAVAILABLE",
                ex.getMessage()));
  }

  @ExceptionHandler(AccountServiceUnavailableException.class)
  public ResponseEntity<ErrorResponse> handleAccountUnavailable(
      AccountServiceUnavailableException ex) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(
            ErrorResponse.of(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                "ACCOUNT_SERVICE_UNAVAILABLE",
                ex.getMessage()));
  }

  @ExceptionHandler(CallNotPermittedException.class)
  public ResponseEntity<ErrorResponse> handleCallNotPermitted(CallNotPermittedException ex) {
    String name = ex.getCausingCircuitBreakerName();
    String error =
        "accountService".equalsIgnoreCase(name)
            ? "ACCOUNT_SERVICE_UNAVAILABLE"
            : "RISK_SERVICE_UNAVAILABLE";
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(ErrorResponse.of(HttpStatus.SERVICE_UNAVAILABLE.value(), error, ex.getMessage()));
  }

  @ExceptionHandler({InvalidOrderException.class, PreTradeRiskException.class})
  public ResponseEntity<ErrorResponse> handleBadRequest(RuntimeException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), "Bad Request", ex.getMessage()));
  }

  @ExceptionHandler(InsufficientFundsException.class)
  public ResponseEntity<ErrorResponse> handleInsufficientFunds(InsufficientFundsException ex) {
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
        .body(
            ErrorResponse.of(
                HttpStatus.UNPROCESSABLE_ENTITY.value(), "Insufficient Funds", ex.getMessage()));
  }

  @ExceptionHandler(OrderNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleOrderNotFound(OrderNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ErrorResponse.of(HttpStatus.NOT_FOUND.value(), "Order Not Found", ex.getMessage()));
  }

  @ExceptionHandler(OrderNotCancellableException.class)
  public ResponseEntity<ErrorResponse> handleNotCancellable(OrderNotCancellableException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(
            ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(), "Order Not Cancellable", ex.getMessage()));
  }

  @ExceptionHandler(DuplicateKeyException.class)
  public ResponseEntity<ErrorResponse> handleDuplicateKey(DuplicateKeyException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(
            ErrorResponse.of(
                HttpStatus.CONFLICT.value(),
                "Duplicate Idempotency Key",
                "An order with this idempotency key already exists"));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
    log.error("Unhandled error in order service: ", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(
            ErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                "An unexpected error occurred"));
  }
}
