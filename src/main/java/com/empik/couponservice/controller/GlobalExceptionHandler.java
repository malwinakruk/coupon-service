package com.empik.couponservice.controller;

import com.empik.couponservice.dto.ErrorResponse;
import com.empik.couponservice.exception.CouponCodeConflictException;
import com.empik.couponservice.exception.InvalidCouponRequestException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates business exceptions into the API's error response shape: a stable machine-readable
 * code plus a human-readable message.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    /**
     * Handles invalid coupon-creation input.
     *
     * @param exception the validation failure
     * @return {@code 400} with error code {@code INVALID_REQUEST}
     */
    @ExceptionHandler(InvalidCouponRequestException.class)
    ResponseEntity<ErrorResponse> handleInvalidRequest(InvalidCouponRequestException exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_REQUEST", exception.getMessage()));
    }

    /**
     * Handles a coupon code that already exists with different data.
     *
     * @param exception the conflict
     * @return {@code 409} with error code {@code CODE_ALREADY_EXISTS}
     */
    @ExceptionHandler(CouponCodeConflictException.class)
    ResponseEntity<ErrorResponse> handleCodeConflict(CouponCodeConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("CODE_ALREADY_EXISTS", exception.getMessage()));
    }
}
