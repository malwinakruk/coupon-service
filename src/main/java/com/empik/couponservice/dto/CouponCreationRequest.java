package com.empik.couponservice.dto;

/**
 * Request body for creating a coupon.
 *
 * @param code coupon code as submitted by the caller (not yet normalized)
 * @param maxUses maximum number of allowed uses
 * @param country ISO 3166-1 alpha-2 country to restrict the coupon to
 */
public record CouponCreationRequest(String code, Integer maxUses, String country) {
}
