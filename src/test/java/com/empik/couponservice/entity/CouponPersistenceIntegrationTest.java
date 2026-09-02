package com.empik.couponservice.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration tests verifying the real database constraints behind {@link Coupon} and
 * {@link CouponUsage} (unique code, CHECK on max_uses/current_uses, unique usage per user).
 * The database is never mocked, per the design doc's test doubles policy.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class CouponPersistenceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private EntityManager entityManager;

    /** A coupon persists with all fields intact and a generated identifier. */
    @Test
    void persistsCouponWithGeneratedId() {
        Coupon coupon = new Coupon("summer2026", 10, "PL");

        entityManager.persist(coupon);
        entityManager.flush();

        assertThat(coupon.getId()).isNotNull();
    }

    /** Two coupons with the same code violate the unique constraint. */
    @Test
    void rejectsDuplicateCode() {
        entityManager.persist(new Coupon("summer2026", 10, "PL"));
        entityManager.flush();

        assertThatThrownBy(() -> {
                    entityManager.persist(new Coupon("summer2026", 5, "DE"));
                    entityManager.flush();
                })
                .isInstanceOf(PersistenceException.class);
    }

    /** A non-positive usage limit violates the CHECK constraint on max_uses. */
    @Test
    void rejectsNonPositiveMaxUses() {
        assertThatThrownBy(() -> {
                    entityManager.persist(new Coupon("badcoupon", 0, "PL"));
                    entityManager.flush();
                })
                .isInstanceOf(PersistenceException.class);
    }

    /** A usage persists once, referencing its coupon. */
    @Test
    void persistsUsageForExistingCoupon() {
        Coupon coupon = new Coupon("winter2026", 3, "PL");
        entityManager.persist(coupon);

        CouponUsage usage = new CouponUsage(coupon, "user-1");
        entityManager.persist(usage);
        entityManager.flush();

        assertThat(usage.getId()).isNotNull();
    }

    /** The same user using the same coupon twice violates the unique constraint. */
    @Test
    void rejectsDuplicateUsageByTheSameUser() {
        Coupon coupon = new Coupon("spring2026", 3, "PL");
        entityManager.persist(coupon);
        entityManager.persist(new CouponUsage(coupon, "user-1"));
        entityManager.flush();

        assertThatThrownBy(() -> {
                    entityManager.persist(new CouponUsage(coupon, "user-1"));
                    entityManager.flush();
                })
                .isInstanceOf(PersistenceException.class);
    }
}
