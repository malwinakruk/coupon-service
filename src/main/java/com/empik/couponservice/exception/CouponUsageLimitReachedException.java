package com.empik.couponservice.exception;

/**
 * Thrown when a coupon has already reached its maximum number of uses.
 */
public class CouponUsageLimitReachedException extends RuntimeException {

    /**
     * Creates the exception for the given exhausted coupon.
     *
     * @param code the coupon code that has reached its usage limit
     */
    public CouponUsageLimitReachedException(String code) {
        super("Coupon has reached its usage limit: " + code);
    }
}
