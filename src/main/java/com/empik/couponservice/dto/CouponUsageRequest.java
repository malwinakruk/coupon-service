package com.empik.couponservice.dto;

/**
 * Request body for redeeming a coupon.
 *
 * @param userId identifier of the user redeeming the coupon
 */
public record CouponUsageRequest(String userId) {
}
