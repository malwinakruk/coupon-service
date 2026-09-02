package com.empik.couponservice.exception;

/**
 * Thrown when a coupon code already exists but with different data than what was requested —
 * a genuine conflict, not the same request retried.
 */
public class CouponCodeConflictException extends RuntimeException {

    /**
     * Creates the exception for the given conflicting code.
     *
     * @param code the coupon code that already exists with different data
     */
    public CouponCodeConflictException(String code) {
        super("Coupon code already exists with different data: " + code);
    }
}
