package com.empik.couponservice.dto;

import com.empik.couponservice.entity.Coupon;
import java.time.OffsetDateTime;

/**
 * Response body representing a coupon.
 *
 * @param id surrogate identifier
 * @param code lowercase-normalized coupon code
 * @param createdAt when the coupon was created
 * @param maxUses maximum number of allowed uses
 * @param currentUses current number of uses
 * @param country ISO 3166-1 alpha-2 country this coupon is restricted to
 */
public record CouponResponse(
        Long id, String code, OffsetDateTime createdAt, Integer maxUses, Integer currentUses, String country) {

    /**
     * Builds a response from a persisted coupon.
     *
     * @param coupon the coupon to convert
     * @return the response representation of the given coupon
     */
    public static CouponResponse from(Coupon coupon) {
        return new CouponResponse(
                coupon.getId(),
                coupon.getCode(),
                coupon.getCreatedAt(),
                coupon.getMaxUses(),
                coupon.getCurrentUses(),
                coupon.getCountry());
    }
}
