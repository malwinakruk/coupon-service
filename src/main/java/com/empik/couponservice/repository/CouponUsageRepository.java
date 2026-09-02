package com.empik.couponservice.repository;

import com.empik.couponservice.entity.CouponUsage;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence operations for {@link CouponUsage}.
 *
 * <p>No custom finder is needed: the unique constraint on ({@code coupon_id}, {@code user_id})
 * enforces one use per user per coupon by rejecting the {@code INSERT} itself — the service
 * layer doesn't need to check existence first, and can't be fooled by two requests checking at
 * the same time and both seeing "not used yet."
 */
public interface CouponUsageRepository extends JpaRepository<CouponUsage, Long> {
}
