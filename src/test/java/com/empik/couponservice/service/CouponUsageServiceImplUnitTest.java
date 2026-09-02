package com.empik.couponservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.empik.couponservice.client.GeoLocationService;
import com.empik.couponservice.entity.Coupon;
import com.empik.couponservice.entity.CouponUsage;
import com.empik.couponservice.exception.CountryNotAllowedException;
import com.empik.couponservice.exception.CouponAlreadyUsedException;
import com.empik.couponservice.exception.CouponNotFoundException;
import com.empik.couponservice.exception.CouponUsageLimitReachedException;
import com.empik.couponservice.exception.InvalidCouponRequestException;
import com.empik.couponservice.repository.CouponRepository;
import com.empik.couponservice.repository.CouponUsageRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Pure unit tests for {@link CouponUsageServiceImpl}: the country-comparison, normalization, and
 * insert/limit branching behind UC2's variants. No Spring context and no database — every
 * dependency is mocked.
 */
class CouponUsageServiceImplUnitTest {

    private CouponRepository couponRepository;
    private CouponUsageRepository couponUsageRepository;
    private GeoLocationService geoLocationService;
    private CouponUsageServiceImpl couponUsageService;

    @BeforeEach
    void setUp() {
        couponRepository = mock(CouponRepository.class);
        couponUsageRepository = mock(CouponUsageRepository.class);
        geoLocationService = mock(GeoLocationService.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        couponUsageService =
                new CouponUsageServiceImpl(couponRepository, couponUsageRepository, geoLocationService, transactionManager);
    }

    /**
     * A missing user ID is rejected before any lookup, instead of failing at the database's
     * NOT NULL constraint and being misread as an already-used conflict.
     */
    @Test
    void rejectsBlankUserIdWithoutLookingUpTheCoupon() {
        assertThatThrownBy(() -> couponUsageService.useCoupon("golden", "  ", "1.2.3.4"))
                .isInstanceOf(InvalidCouponRequestException.class);

        verifyNoInteractions(couponRepository, geoLocationService);
    }

    /** A user ID longer than the database column allows is rejected before any lookup. */
    @Test
    void rejectsUserIdLongerThanTheColumnLimit() {
        String tooLong = "a".repeat(256);

        assertThatThrownBy(() -> couponUsageService.useCoupon("golden", tooLong, "1.2.3.4"))
                .isInstanceOf(InvalidCouponRequestException.class);

        verifyNoInteractions(couponRepository, geoLocationService);
    }

    /** Variant B: an unknown code is rejected before any geolocation call, using the normalized code. */
    @Test
    void missingCouponShortCircuitsBeforeGeoLocation() {
        when(couponRepository.findByCode("golden")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> couponUsageService.useCoupon("GOLDEN", "user-1", "1.2.3.4"))
                .isInstanceOf(CouponNotFoundException.class);

        verify(couponRepository).findByCode("golden");
        verifyNoInteractions(geoLocationService);
    }

    /** Variant D: a country mismatch is rejected and no usage insert is attempted. */
    @Test
    void countryMismatchThrowsWithoutInserting() {
        Coupon coupon = new Coupon("golden", 5, "PL");
        when(couponRepository.findByCode("golden")).thenReturn(Optional.of(coupon));
        when(geoLocationService.lookupCountry("1.2.3.4")).thenReturn("DE");

        assertThatThrownBy(() -> couponUsageService.useCoupon("golden", "user-1", "1.2.3.4"))
                .isInstanceOf(CountryNotAllowedException.class);

        verify(couponUsageRepository, never()).saveAndFlush(any());
    }

    /** Variant E: a duplicate usage insert is translated into an already-used failure. */
    @Test
    void duplicateInsertTranslatesToAlreadyUsed() {
        Coupon coupon = new Coupon("golden", 5, "PL");
        when(couponRepository.findByCode("golden")).thenReturn(Optional.of(coupon));
        when(geoLocationService.lookupCountry("1.2.3.4")).thenReturn("PL");
        when(couponUsageRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> couponUsageService.useCoupon("golden", "user-1", "1.2.3.4"))
                .isInstanceOf(CouponAlreadyUsedException.class);

        verify(couponRepository, never()).incrementUsageIfUnderLimit(any());
    }

    /** Variant F: zero rows changed by the atomic update is translated into a limit-reached failure. */
    @Test
    void zeroRowsUpdatedTranslatesToLimitReached() {
        Coupon coupon = new Coupon("golden", 1, "PL");
        when(couponRepository.findByCode("golden")).thenReturn(Optional.of(coupon));
        when(geoLocationService.lookupCountry("1.2.3.4")).thenReturn("PL");
        when(couponUsageRepository.saveAndFlush(any())).thenReturn(new CouponUsage(coupon, "user-1"));
        when(couponRepository.incrementUsageIfUnderLimit(any())).thenReturn(0);

        assertThatThrownBy(() -> couponUsageService.useCoupon("golden", "user-1", "1.2.3.4"))
                .isInstanceOf(CouponUsageLimitReachedException.class);
    }

    /** Variant A: a matching country and a successful update return the recorded usage. */
    @Test
    void successReturnsTheRecordedUsage() {
        Coupon coupon = new Coupon("golden", 5, "PL");
        when(couponRepository.findByCode("golden")).thenReturn(Optional.of(coupon));
        when(geoLocationService.lookupCountry("1.2.3.4")).thenReturn("PL");
        CouponUsage usage = new CouponUsage(coupon, "user-1");
        when(couponUsageRepository.saveAndFlush(any())).thenReturn(usage);
        when(couponRepository.incrementUsageIfUnderLimit(any())).thenReturn(1);

        CouponUsage result = couponUsageService.useCoupon("golden", "user-1", "1.2.3.4");

        assertThat(result).isSameAs(usage);
    }
}
