package com.empik.couponservice.exception;

/**
 * Thrown when a request to create or redeem a coupon fails input validation (code format,
 * usage limit, country format, or user ID).
 */
public class InvalidCouponRequestException extends RuntimeException {

    /**
     * Creates the exception with a message describing which part of the request was invalid.
     *
     * @param message human-readable description of the validation failure
     */
    public InvalidCouponRequestException(String message) {
        super(message);
    }
}
