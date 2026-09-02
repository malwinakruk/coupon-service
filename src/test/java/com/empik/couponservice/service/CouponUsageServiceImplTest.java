package com.empik.couponservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.empik.couponservice.client.GeoLocationService;
import com.empik.couponservice.entity.Coupon;
import com.empik.couponservice.entity.CouponUsage;
import com.empik.couponservice.exception.CountryNotAllowedException;
import com.empik.couponservice.exception.CouponAlreadyUsedException;
import com.empik.couponservice.exception.CouponNotFoundException;
import com.empik.couponservice.exception.CouponUsageLimitReachedException;
import com.empik.couponservice.exception.GeoLocationUnavailableException;
import com.empik.couponservice.repository.CouponRepository;
import com.empik.couponservice.repository.CouponUsageRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration tests for {@link CouponUsageServiceImpl}, covering UC2's variants. Real Postgres
 * (Testcontainers) so the unique-constraint and usage-limit checks are exercised for real.
 * {@link GeoLocationService} is mocked, per the design doc's test-double policy — it's the only
 * external dependency.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(CouponUsageServiceImpl.class)
@Testcontainers
class CouponUsageServiceImplTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private CouponUsageService couponUsageService;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private CouponUsageRepository couponUsageRepository;

    @MockitoBean
    private GeoLocationService geoLocationService;

    /** Variant A: matching country registers the usage and increments the coupon's count. */
    @Test
    void registersUsageOnSuccess() {
        Coupon coupon = couponRepository.saveAndFlush(new Coupon("golden", 5, "PL"));
        when(geoLocationService.lookupCountry("1.2.3.4")).thenReturn("PL");

        CouponUsage usage = couponUsageService.useCoupon("golden", "user-1", "1.2.3.4");

        assertThat(usage.getUserId()).isEqualTo("user-1");
        assertThat(couponRepository.findById(coupon.getId()).orElseThrow().getCurrentUses())
                .isEqualTo(1);
    }

    /** Variant B: an unknown code is rejected before any geolocation call is made. */
    @Test
    void missingCouponThrows() {
        assertThatThrownBy(() -> couponUsageService.useCoupon("missing", "user-1", "1.2.3.4"))
                .isInstanceOf(CouponNotFoundException.class);
    }

    /** Variant C: a geolocation failure propagates and no usage is registered. */
    @Test
    void geoLocationFailurePropagates() {
        Coupon coupon = couponRepository.saveAndFlush(new Coupon("unstable", 5, "PL"));
        when(geoLocationService.lookupCountry(any())).thenThrow(new GeoLocationUnavailableException("1.2.3.4", null));

        assertThatThrownBy(() -> couponUsageService.useCoupon("unstable", "user-1", "1.2.3.4"))
                .isInstanceOf(GeoLocationUnavailableException.class);
        assertThat(usagesFor(coupon)).isEmpty();
    }

    /** Variant D: a country mismatch is rejected and no usage is registered. */
    @Test
    void wrongCountryThrows() {
        Coupon coupon = couponRepository.saveAndFlush(new Coupon("faraway", 5, "PL"));
        when(geoLocationService.lookupCountry("1.2.3.4")).thenReturn("DE");

        assertThatThrownBy(() -> couponUsageService.useCoupon("faraway", "user-1", "1.2.3.4"))
                .isInstanceOf(CountryNotAllowedException.class);
        assertThat(usagesFor(coupon)).isEmpty();
    }

    /** Variant E: the same user redeeming twice is rejected the second time. */
    @Test
    void repeatUseByTheSameUserThrows() {
        couponRepository.saveAndFlush(new Coupon("onceonly", 5, "PL"));
        when(geoLocationService.lookupCountry("1.2.3.4")).thenReturn("PL");
        couponUsageService.useCoupon("onceonly", "user-1", "1.2.3.4");

        assertThatThrownBy(() -> couponUsageService.useCoupon("onceonly", "user-1", "1.2.3.4"))
                .isInstanceOf(CouponAlreadyUsedException.class);
    }

    /**
     * Variant F: once a coupon is at its limit, a different user's attempt is rejected and its
     * usage insert is rolled back, not left as an orphan record.
     *
     * <p>{@code NOT_SUPPORTED} suspends {@code @DataJpaTest}'s own wrapping transaction, so the
     * service's transaction is a real, standalone one — otherwise it would just join the test's
     * transaction and the rollback wouldn't be visible to the assertions below.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void usageLimitReachedRollsBackTheInsert() {
        Coupon coupon = couponRepository.saveAndFlush(new Coupon("exhausted", 1, "PL"));
        when(geoLocationService.lookupCountry("1.2.3.4")).thenReturn("PL");
        couponUsageService.useCoupon("exhausted", "user-1", "1.2.3.4");

        assertThatThrownBy(() -> couponUsageService.useCoupon("exhausted", "user-2", "1.2.3.4"))
                .isInstanceOf(CouponUsageLimitReachedException.class);

        assertThat(usagesFor(coupon)).hasSize(1);
        assertThat(couponRepository.findById(coupon.getId()).orElseThrow().getCurrentUses())
                .isEqualTo(1);
    }

    private List<CouponUsage> usagesFor(Coupon coupon) {
        return couponUsageRepository.findAll().stream()
                .filter(usage -> usage.getCoupon().getId().equals(coupon.getId()))
                .toList();
    }
}
