package com.empik.couponservice.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.empik.couponservice.entity.Coupon;
import com.empik.couponservice.entity.CouponUsage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration tests for {@link CouponUsageRepository}. Real Postgres (Testcontainers) — no test
 * doubles, per the design doc's policy.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class CouponUsageRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private CouponUsageRepository couponUsageRepository;

    /** A usage persists and reloads with its coupon reference intact. */
    @Test
    void savesAndReloadsWithCouponReference() {
        Coupon coupon = couponRepository.saveAndFlush(new Coupon("usage-test", 5, "PL"));

        CouponUsage saved = couponUsageRepository.saveAndFlush(new CouponUsage(coupon, "user-1"));

        CouponUsage reloaded = couponUsageRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getCoupon().getId()).isEqualTo(coupon.getId());
        assertThat(reloaded.getUserId()).isEqualTo("user-1");
    }
}
