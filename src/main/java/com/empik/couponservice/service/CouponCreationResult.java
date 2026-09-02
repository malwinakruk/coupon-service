package com.empik.couponservice.service;

import com.empik.couponservice.entity.Coupon;

/**
 * Outcome of {@link CouponService#createCoupon}. Distinguishes a brand-new coupon from an
 * identical request retried, so the caller can choose between a 201 and a 200 response.
 *
 * @param coupon the created or matching existing coupon
 * @param created {@code true} if this call created the coupon, {@code false} if an identical
 *     one already existed
 */
public record CouponCreationResult(Coupon coupon, boolean created) {
}
