package com.empik.couponservice.exception;

/**
 * Thrown when no coupon exists for the requested code.
 */
public class CouponNotFoundException extends RuntimeException {

    /**
     * Creates the exception for the given missing code.
     *
     * @param code the coupon code that could not be found
     */
    public CouponNotFoundException(String code) {
        super("No coupon found for code: " + code);
    }
}
