package com.empik.couponservice.exception;

/**
 * Thrown when the same user has already used the given coupon.
 */
public class CouponAlreadyUsedException extends RuntimeException {

    /**
     * Creates the exception for the given repeat use.
     *
     * @param code the coupon code
     * @param userId the user who already used it
     */
    public CouponAlreadyUsedException(String code, String userId) {
        super("User " + userId + " has already used coupon: " + code);
    }
}
