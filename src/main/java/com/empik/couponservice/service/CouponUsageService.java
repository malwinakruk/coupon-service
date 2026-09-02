package com.empik.couponservice.service;

import com.empik.couponservice.entity.CouponUsage;
import com.empik.couponservice.exception.CountryNotAllowedException;
import com.empik.couponservice.exception.CouponAlreadyUsedException;
import com.empik.couponservice.exception.CouponNotFoundException;
import com.empik.couponservice.exception.CouponUsageLimitReachedException;
import com.empik.couponservice.exception.GeoLocationUnavailableException;
import com.empik.couponservice.exception.InvalidCouponRequestException;

/**
 * Business logic for redeeming coupons.
 */
public interface CouponUsageService {

    /**
     * Registers a use of a coupon by a user, after checking that the user's geolocated country
     * matches the coupon's.
     *
     * @param code coupon code as submitted by the caller (not yet normalized)
     * @param userId identifier of the user redeeming the coupon
     * @param ipAddress IP address the request came from, used to geolocate the user
     * @return the recorded usage
     * @throws InvalidCouponRequestException if the coupon code is missing, or the user ID is
     *     missing or too long
     * @throws CouponNotFoundException if no coupon exists for the given code
     * @throws GeoLocationUnavailableException if the user's country can't be determined
     * @throws CountryNotAllowedException if the user's country doesn't match the coupon's
     * @throws CouponAlreadyUsedException if this user has already used this coupon
     * @throws CouponUsageLimitReachedException if the coupon has reached its usage limit
     */
    CouponUsage useCoupon(String code, String userId, String ipAddress);
}
