package com.empik.couponservice.service;

import com.empik.couponservice.exception.CouponCodeConflictException;
import com.empik.couponservice.exception.InvalidCouponRequestException;

/**
 * Business logic for creating coupons.
 */
public interface CouponService {

    /**
     * Creates a coupon, or returns the existing one if this is the same request retried.
     *
     * @param code coupon code as submitted by the caller (not yet normalized)
     * @param maxUses maximum number of allowed uses
     * @param country ISO 3166-1 alpha-2 country to restrict the coupon to
     * @return the result, indicating whether a new coupon was created
     * @throws InvalidCouponRequestException if the code, usage limit, or country fails validation
     * @throws CouponCodeConflictException if the code already exists with different data
     */
    CouponCreationResult createCoupon(String code, Integer maxUses, String country);
}
