package com.empik.couponservice.dto;

/**
 * Request body for redeeming a coupon.
 *
 * @param code coupon code as submitted by the caller (not yet normalized)
 * @param userId identifier of the user redeeming the coupon
 */
public record CouponUsageRequest(String code, String userId) {
}
