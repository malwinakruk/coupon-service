package com.empik.couponservice.service;

import com.empik.couponservice.entity.Coupon;
import com.empik.couponservice.exception.CouponCodeConflictException;
import com.empik.couponservice.exception.InvalidCouponRequestException;
import com.empik.couponservice.repository.CouponRepository;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Default {@link CouponService} implementation.
 */
@Service
class CouponServiceImpl implements CouponService {

    private static final Logger LOG = LoggerFactory.getLogger(CouponServiceImpl.class);

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");
    private static final Pattern COUNTRY_PATTERN = Pattern.compile("^[A-Za-z]{2}$");

    private final CouponRepository couponRepository;
    private final TransactionTemplate insertTransaction;

    /**
     * Creates the service with its repository dependency.
     *
     * @param couponRepository repository used to persist and look up coupons
     * @param transactionManager used to run each insert attempt in its own fresh transaction, so
     *     a unique-constraint violation aborts only that insert; Postgres poisons the whole
     *     transaction on any SQL error, so reusing the caller's transaction would make the
     *     fallback lookup fail too
     */
    CouponServiceImpl(CouponRepository couponRepository, PlatformTransactionManager transactionManager) {
        this.couponRepository = couponRepository;
        this.insertTransaction = new TransactionTemplate(transactionManager);
        this.insertTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public CouponCreationResult createCoupon(String code, Integer maxUses, String country) {
        validateCoupon(code, maxUses, country);
        String normalizedCode = code.toLowerCase();
        String normalizedCountry = country.toUpperCase();
        LOG.debug("Creating coupon code={} maxUses={} country={}", normalizedCode, maxUses, normalizedCountry);

        try {
            Coupon coupon = insertTransaction.execute(
                    status -> couponRepository.saveAndFlush(new Coupon(normalizedCode, maxUses, normalizedCountry)));
            LOG.info("Created coupon code={} maxUses={} country={}", normalizedCode, maxUses, normalizedCountry);
            return new CouponCreationResult(coupon, true);
        } catch (DataIntegrityViolationException e) {
            return handleCodeConflict(normalizedCode, maxUses, normalizedCountry);
        }
    }

    /**
     * Distinguishes a genuine conflict from the same request retried, once the unique constraint
     * on {@code code} has already rejected the insert.
     */
    private CouponCreationResult handleCodeConflict(String normalizedCode, Integer maxUses, String normalizedCountry) {
        Coupon existing = couponRepository
                .findByCode(normalizedCode)
                .orElseThrow(() -> new IllegalStateException(
                        "Coupon code conflicted on insert but is no longer found: " + normalizedCode));

        boolean sameRequest =
                existing.getMaxUses().equals(maxUses) && existing.getCountry().equals(normalizedCountry);
        if (!sameRequest) {
            LOG.warn("Coupon code {} already exists with different data", normalizedCode);
            throw new CouponCodeConflictException(normalizedCode);
        }
        LOG.info("Coupon code {} already exists with identical data, treating as idempotent retry", normalizedCode);
        return new CouponCreationResult(existing, false);
    }

    private void validateCoupon(String code, Integer maxUses, String country) {
        if (code == null || !CODE_PATTERN.matcher(code).matches()) {
            throw new InvalidCouponRequestException(
                    "Coupon code must be 1-64 characters, containing only letters, digits, '-', and '_'");
        }
        if (maxUses == null || maxUses <= 0) {
            throw new InvalidCouponRequestException("Maximum number of uses must be greater than zero");
        }
        if (country == null || !COUNTRY_PATTERN.matcher(country).matches()) {
            throw new InvalidCouponRequestException("Country must be a 2-letter ISO 3166-1 alpha-2 code");
        }
    }
}
