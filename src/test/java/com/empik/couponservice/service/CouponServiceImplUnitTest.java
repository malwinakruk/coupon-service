package com.empik.couponservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.empik.couponservice.entity.Coupon;
import com.empik.couponservice.exception.CouponCodeConflictException;
import com.empik.couponservice.exception.InvalidCouponRequestException;
import com.empik.couponservice.repository.CouponRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Pure unit tests for {@link CouponServiceImpl}: validation, normalization, and the
 * idempotency-comparison logic behind UC1 Variant C vs D. No Spring context and no database —
 * {@link CouponRepository} and the transaction manager are both mocked.
 */
class CouponServiceImplUnitTest {

    private CouponRepository couponRepository;
    private CouponServiceImpl couponService;

    @BeforeEach
    void setUp() {
        couponRepository = mock(CouponRepository.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        couponService = new CouponServiceImpl(couponRepository, transactionManager);
    }

    /** Variant B: an invalid code is rejected before touching the repository. */
    @Test
    void rejectsInvalidCodeWithoutTouchingTheRepository() {
        assertThatThrownBy(() -> couponService.createCoupon("bad code", 5, "PL"))
                .isInstanceOf(InvalidCouponRequestException.class);

        verify(couponRepository, never()).saveAndFlush(any());
    }

    /** Variant B: a non-positive usage limit is rejected. */
    @Test
    void rejectsNonPositiveMaxUses() {
        assertThatThrownBy(() -> couponService.createCoupon("summer", 0, "PL"))
                .isInstanceOf(InvalidCouponRequestException.class);
    }

    /** Variant B: a country that isn't a 2-letter code is rejected. */
    @Test
    void rejectsInvalidCountryFormat() {
        assertThatThrownBy(() -> couponService.createCoupon("summer", 5, "POL"))
                .isInstanceOf(InvalidCouponRequestException.class);
    }

    /**
     * Variant B: a code longer than the database column allows is rejected before the insert
     * attempt, instead of failing at the database and being misread as a code conflict.
     */
    @Test
    void rejectsCodeLongerThanTheColumnLimit() {
        String tooLong = "a".repeat(65);

        assertThatThrownBy(() -> couponService.createCoupon(tooLong, 5, "PL"))
                .isInstanceOf(InvalidCouponRequestException.class);

        verify(couponRepository, never()).saveAndFlush(any());
    }

    /** Boundary: a code exactly at the 64-character column limit is accepted. */
    @Test
    void acceptsCodeAtTheColumnLimit() {
        when(couponRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        String maxLength = "a".repeat(64);

        CouponCreationResult result = couponService.createCoupon(maxLength, 5, "PL");

        assertThat(result.created()).isTrue();
        assertThat(result.coupon().getCode()).isEqualTo(maxLength);
    }

    /** ADR-6: the code and country are normalized before being saved. */
    @Test
    void normalizesCodeAndCountryBeforeSaving() {
        when(couponRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        couponService.createCoupon("SPRING", 5, "pl");

        ArgumentCaptor<Coupon> captor = ArgumentCaptor.forClass(Coupon.class);
        verify(couponRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("spring");
        assertThat(captor.getValue().getCountry()).isEqualTo("PL");
    }

    /** Variant D: a conflict whose existing data matches exactly is treated as a retry. */
    @Test
    void identicalConflictIsTreatedAsRetry() {
        when(couponRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));
        Coupon existing = new Coupon("summer", 5, "PL");
        when(couponRepository.findByCode("summer")).thenReturn(Optional.of(existing));

        CouponCreationResult result = couponService.createCoupon("SUMMER", 5, "pl");

        assertThat(result.created()).isFalse();
        assertThat(result.coupon()).isSameAs(existing);
    }

    /** Variant C: a conflict whose existing data differs is a genuine conflict. */
    @Test
    void differingConflictThrows() {
        when(couponRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));
        when(couponRepository.findByCode("summer")).thenReturn(Optional.of(new Coupon("summer", 10, "PL")));

        assertThatThrownBy(() -> couponService.createCoupon("summer", 5, "PL"))
                .isInstanceOf(CouponCodeConflictException.class);
    }
}
