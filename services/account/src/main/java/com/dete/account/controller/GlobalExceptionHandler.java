package com.dete.account.controller;

import com.dete.account.exception.AccountNotFoundException;
import com.dete.account.exception.DuplicateSettlementException;
import com.dete.account.exception.InsufficientFundsException;
import com.dete.account.exception.InvalidOperationException;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
  public ResponseEntity<Map<String, Object>> handleValidationException(
      MethodArgumentNotValidException ex) {
    String errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining(", "));
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(
            Map.of(
                "status",
                HttpStatus.BAD_REQUEST.value(),
                "error",
                "Validation Failed",
                "message",
                errors,
                "timestamp",
                Instant.now().toString()));
  }

  @ExceptionHandler(InsufficientFundsException.class)
  public ResponseEntity<Map<String, Object>> handleInsufficientFunds(
      InsufficientFundsException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(
            Map.of(
                "status", HttpStatus.BAD_REQUEST.value(),
                "error", "Insufficient Funds",
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()));
  }

  @ExceptionHandler(AccountNotFoundException.class)
  public ResponseEntity<Map<String, Object>> handleAccountNotFound(AccountNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(
            Map.of(
                "status", HttpStatus.NOT_FOUND.value(),
                "error", "Account Not Found",
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()));
  }

  @ExceptionHandler(DuplicateSettlementException.class)
  public ResponseEntity<Map<String, Object>> handleDuplicateSettlement(
      DuplicateSettlementException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(
            Map.of(
                "status", HttpStatus.CONFLICT.value(),
                "error", "Duplicate Settlement",
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()));
  }

  @ExceptionHandler(InvalidOperationException.class)
  public ResponseEntity<Map<String, Object>> handleInvalidOperation(InvalidOperationException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(
            Map.of(
                "status", HttpStatus.BAD_REQUEST.value(),
                "error", "Invalid Operation",
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
    log.error("Unhandled error: ", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(
            Map.of(
                "status",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "error",
                "Internal Server Error",
                "message",
                "An unexpected error occurred",
                "timestamp",
                Instant.now().toString()));
  }
}
