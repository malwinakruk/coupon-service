package com.empik.couponservice.controller;

import com.empik.couponservice.dto.ErrorResponse;
import com.empik.couponservice.exception.CountryNotAllowedException;
import com.empik.couponservice.exception.CouponAlreadyUsedException;
import com.empik.couponservice.exception.CouponCodeConflictException;
import com.empik.couponservice.exception.CouponNotFoundException;
import com.empik.couponservice.exception.CouponUsageLimitReachedException;
import com.empik.couponservice.exception.GeoLocationUnavailableException;
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
     * Handles invalid request input, whether creating or redeeming a coupon.
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

    /**
     * Handles a redemption request for a code that doesn't exist.
     *
     * @param exception the missing-coupon failure
     * @return {@code 404} with error code {@code COUPON_NOT_FOUND}
     */
    @ExceptionHandler(CouponNotFoundException.class)
    ResponseEntity<ErrorResponse> handleCouponNotFound(CouponNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("COUPON_NOT_FOUND", exception.getMessage()));
    }

    /**
     * Handles the geolocation provider being unreachable, even after retries.
     *
     * @param exception the geolocation failure
     * @return {@code 503} with error code {@code GEO_UNAVAILABLE}
     */
    @ExceptionHandler(GeoLocationUnavailableException.class)
    ResponseEntity<ErrorResponse> handleGeoLocationUnavailable(GeoLocationUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse("GEO_UNAVAILABLE", exception.getMessage()));
    }

    /**
     * Handles a redemption attempt from outside the coupon's target country.
     *
     * @param exception the country mismatch
     * @return {@code 403} with error code {@code COUNTRY_NOT_ALLOWED}
     */
    @ExceptionHandler(CountryNotAllowedException.class)
    ResponseEntity<ErrorResponse> handleCountryNotAllowed(CountryNotAllowedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("COUNTRY_NOT_ALLOWED", exception.getMessage()));
    }

    /**
     * Handles a user trying to redeem the same coupon more than once.
     *
     * @param exception the repeat-use failure
     * @return {@code 409} with error code {@code ALREADY_USED}
     */
    @ExceptionHandler(CouponAlreadyUsedException.class)
    ResponseEntity<ErrorResponse> handleAlreadyUsed(CouponAlreadyUsedException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("ALREADY_USED", exception.getMessage()));
    }

    /**
     * Handles a redemption attempt on a coupon that has reached its usage limit.
     *
     * @param exception the exhausted-limit failure
     * @return {@code 409} with error code {@code LIMIT_REACHED}
     */
    @ExceptionHandler(CouponUsageLimitReachedException.class)
    ResponseEntity<ErrorResponse> handleLimitReached(CouponUsageLimitReachedException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("LIMIT_REACHED", exception.getMessage()));
    }
}
