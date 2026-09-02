package com.empik.couponservice.repository;

import com.empik.couponservice.entity.Coupon;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistence operations for {@link Coupon}.
 */
public interface CouponRepository extends JpaRepository<Coupon, Long> {

    /**
     * Finds a coupon by its normalized (lowercase) code.
     *
     * @param code the normalized code to look up
     * @return the matching coupon, if any
     */
    Optional<Coupon> findByCode(String code);

    /**
     * Atomically increments {@code currentUses} if the coupon is still under its limit —
     * bypasses the entity's normal load-then-save flow, which would otherwise re-check a value
     * already read into memory and let concurrent requests push the count past the limit.
     *
     * @param id the coupon to increment
     * @return 1 if the increment applied, 0 if the coupon was already at its usage limit
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Coupon c SET c.currentUses = c.currentUses + 1 WHERE c.id = :id AND c.currentUses < c.maxUses")
    int incrementUsageIfUnderLimit(@Param("id") Long id);
}
