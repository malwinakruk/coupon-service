package com.empik.couponservice.dto;

import com.empik.couponservice.entity.CouponUsage;
import java.time.OffsetDateTime;

/**
 * Response body confirming a coupon redemption.
 *
 * @param code the redeemed coupon's code
 * @param userId identifier of the user who redeemed it
 * @param usedAt when the usage was registered
 */
public record CouponUsageResponse(String code, String userId, OffsetDateTime usedAt) {

    /**
     * Builds a response from a persisted usage.
     *
     * @param usage the usage to convert
     * @return the response representation of the given usage
     */
    public static CouponUsageResponse from(CouponUsage usage) {
        return new CouponUsageResponse(usage.getCoupon().getCode(), usage.getUserId(), usage.getUsedAt());
    }
}
