package com.empik.couponservice.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CouponUsage}'s construction logic. No Spring context, no database.
 */
class CouponUsageTest {

    /** The constructor sets the coupon, user id, and a fresh timestamp. */
    @Test
    void constructorSetsFieldsAndTimestamp() {
        Coupon coupon = new Coupon("summer2026", 10, "PL");

        CouponUsage usage = new CouponUsage(coupon, "user-1");

        assertThat(usage.getCoupon()).isSameAs(coupon);
        assertThat(usage.getUserId()).isEqualTo("user-1");
        assertThat(usage.getUsedAt()).isCloseTo(OffsetDateTime.now(), within(2, ChronoUnit.SECONDS));
    }
}
