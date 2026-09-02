package com.empik.couponservice.exception;

/**
 * Thrown when a coupon-creation request fails input validation (code format, usage limit,
 * or country format).
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
