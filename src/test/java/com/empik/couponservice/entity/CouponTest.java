package com.empik.couponservice.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Coupon}'s construction logic. No Spring context, no database.
 */
class CouponTest {

    /** The constructor sets every field and starts the usage count at zero. */
    @Test
    void constructorSetsFieldsAndStartsAtZeroUses() {
        Coupon coupon = new Coupon("summer2026", 10, "PL");

        assertThat(coupon.getCode()).isEqualTo("summer2026");
        assertThat(coupon.getMaxUses()).isEqualTo(10);
        assertThat(coupon.getCountry()).isEqualTo("PL");
        assertThat(coupon.getCurrentUses()).isZero();
        assertThat(coupon.getCreatedAt()).isCloseTo(OffsetDateTime.now(), within(2, ChronoUnit.SECONDS));
    }
}
