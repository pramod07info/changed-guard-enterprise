package com.changeguard.impact;

import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> invalid(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> malformed(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Body must be a valid impact request JSON object"));
    }
}
