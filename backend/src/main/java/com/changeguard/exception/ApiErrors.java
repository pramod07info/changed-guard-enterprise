package com.changeguard.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.http.ProblemDetail;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(InvalidContractException.class)
    public ResponseEntity<ProblemDetail> invalid(InvalidContractException exception) {
        return ResponseEntity.badRequest().body(
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> malformed(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Body must be a valid impact request JSON object"));
    }
}
