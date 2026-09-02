package com.empik.couponservice.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.empik.couponservice.entity.Coupon;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration tests for {@link CouponRepository}'s finder and the single-row limit check of its
 * atomic update. Real Postgres (Testcontainers) — no test doubles, per the design doc's policy.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class CouponRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private CouponRepository couponRepository;

    /** An existing code is found; a missing one returns empty. */
    @Test
    void findsByCodeOrReturnsEmpty() {
        couponRepository.save(new Coupon("findme", 5, "PL"));

        assertThat(couponRepository.findByCode("findme")).isPresent();
        assertThat(couponRepository.findByCode("doesnotexist")).isEmpty();
    }

    /** Incrementing a coupon still under its limit succeeds and persists the new count. */
    @Test
    void incrementSucceedsUnderLimit() {
        Coupon coupon = couponRepository.saveAndFlush(new Coupon("underlimit", 5, "PL"));

        int updated = couponRepository.incrementUsageIfUnderLimit(coupon.getId());

        assertThat(updated).isEqualTo(1);
        assertThat(couponRepository.findById(coupon.getId()).orElseThrow().getCurrentUses()).isEqualTo(1);
    }

    /** Incrementing a coupon already at its limit changes nothing and reports zero rows (NFR1). */
    @Test
    void incrementFailsAtLimit() {
        Coupon coupon = new Coupon("atlimit", 1, "PL");
        Coupon saved = couponRepository.saveAndFlush(coupon);
        int firstIncrement = couponRepository.incrementUsageIfUnderLimit(saved.getId());
        assertThat(firstIncrement).isEqualTo(1);

        int updated = couponRepository.incrementUsageIfUnderLimit(saved.getId());

        assertThat(updated).isZero();
        assertThat(couponRepository.findById(saved.getId()).orElseThrow().getCurrentUses()).isEqualTo(1);
    }
}
