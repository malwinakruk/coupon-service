package com.empik.couponservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.empik.couponservice.exception.CouponCodeConflictException;
import com.empik.couponservice.exception.InvalidCouponRequestException;
import com.empik.couponservice.repository.CouponRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration tests for {@link CouponServiceImpl}, covering UC1's four variants. Real Postgres
 * (Testcontainers) so the unique-constraint conflict path is exercised for real, not mocked.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(CouponServiceImpl.class)
@Testcontainers
class CouponServiceImplTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private CouponService couponService;

    @Autowired
    private CouponRepository couponRepository;

    /** Variant A: a brand-new code is created, normalized to lowercase/uppercase. */
    @Test
    void createsNewCoupon() {
        CouponCreationResult result = couponService.createCoupon("SPRING", 10, "pl");

        assertThat(result.created()).isTrue();
        assertThat(result.coupon().getCode()).isEqualTo("spring");
        assertThat(result.coupon().getMaxUses()).isEqualTo(10);
        assertThat(result.coupon().getCountry()).isEqualTo("PL");
        assertThat(couponRepository.findByCode("spring")).isPresent();
    }

    /** Variant B: an invalid code is rejected before anything is persisted. */
    @Test
    void rejectsInvalidCode() {
        assertThatThrownBy(() -> couponService.createCoupon("has space", 10, "PL"))
                .isInstanceOf(InvalidCouponRequestException.class);
    }

    /** Variant B: a non-positive usage limit is rejected. */
    @Test
    void rejectsNonPositiveMaxUses() {
        assertThatThrownBy(() -> couponService.createCoupon("summer", 0, "PL"))
                .isInstanceOf(InvalidCouponRequestException.class);
    }

    /** Variant B: a country that isn't a 2-letter code is rejected. */
    @Test
    void rejectsInvalidCountry() {
        assertThatThrownBy(() -> couponService.createCoupon("summer", 10, "POL"))
                .isInstanceOf(InvalidCouponRequestException.class);
    }

    /** Variant D: the same request retried, differing only by code casing, is idempotent. */
    @Test
    void retryingSameRequestIsIdempotent() {
        CouponCreationResult first = couponService.createCoupon("WIOSNA", 5, "PL");
        CouponCreationResult second = couponService.createCoupon("wiosna", 5, "pl");

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.coupon().getId()).isEqualTo(first.coupon().getId());
    }

    /** Variant C: the same code with different data is a genuine conflict, not a retry. */
    @Test
    void conflictingDataOnExistingCodeThrows() {
        couponService.createCoupon("autumn", 5, "PL");

        assertThatThrownBy(() -> couponService.createCoupon("autumn", 10, "PL"))
                .isInstanceOf(CouponCodeConflictException.class);
    }
}
