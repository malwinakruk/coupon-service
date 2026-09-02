package com.empik.couponservice.service;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Default {@link CouponUsageService} implementation.
 */
@Service
class CouponUsageServiceImpl implements CouponUsageService {

    private static final Logger LOG = LoggerFactory.getLogger(CouponUsageServiceImpl.class);

    private static final int MAX_USER_ID_LENGTH = 255;

    private final CouponRepository couponRepository;
    private final CouponUsageRepository couponUsageRepository;
    private final GeoLocationService geoLocationService;
    private final TransactionTemplate usageTransaction;

    /**
     * Creates the service with its dependencies.
     *
     * @param couponRepository repository used to look up coupons and apply the atomic
     *     usage-limit update
     * @param couponUsageRepository repository used to record individual usages
     * @param geoLocationService used to resolve the user's country from their IP address
     * @param transactionManager used to run the usage write in its own transaction, opened only
     *     after the external geolocation call returns, so no database transaction sits open for
     *     the duration of that call
     */
    CouponUsageServiceImpl(
            CouponRepository couponRepository,
            CouponUsageRepository couponUsageRepository,
            GeoLocationService geoLocationService,
            PlatformTransactionManager transactionManager) {
        this.couponRepository = couponRepository;
        this.couponUsageRepository = couponUsageRepository;
        this.geoLocationService = geoLocationService;
        this.usageTransaction = new TransactionTemplate(transactionManager);
    }

    @Override
    public CouponUsage useCoupon(String code, String userId, String ipAddress) {
        LOG.debug("Redeem attempt code={} userId={} ipAddress={}", code, userId, ipAddress);
        validateCode(code);
        validateUserId(userId);

        Optional<Coupon> maybeCoupon = couponRepository.findByCode(code.toLowerCase());
        if (maybeCoupon.isEmpty()) {
            LOG.warn("No coupon found for code={}", code);
            throw new CouponNotFoundException(code);
        }
        Coupon coupon = maybeCoupon.get();

        String userCountry = geoLocationService.lookupCountry(ipAddress);
        if (!userCountry.equals(coupon.getCountry())) {
            LOG.warn(
                    "Coupon {} rejected: user country {} does not match coupon country {}",
                    coupon.getCode(),
                    userCountry,
                    coupon.getCountry());
            throw new CountryNotAllowedException(userCountry, coupon.getCountry());
        }

        CouponUsage usage = usageTransaction.execute(status -> registerUsage(coupon, userId));
        // Logged only after the transaction commits, so a commit failure can't leave a false success line.
        LOG.info("Coupon {} used by user {}", coupon.getCode(), userId);
        return usage;
    }

    /**
     * Inserts the usage record and applies the atomic usage-limit update in one transaction, so
     * a limit-reached failure rolls back the insert too, not just the update.
     */
    private CouponUsage registerUsage(Coupon coupon, String userId) {
        CouponUsage usage;
        try {
            usage = couponUsageRepository.saveAndFlush(new CouponUsage(coupon, userId));
        } catch (DataIntegrityViolationException e) {
            LOG.warn("Coupon {} already used by user {}", coupon.getCode(), userId);
            throw new CouponAlreadyUsedException(coupon.getCode(), userId);
        }

        if (couponRepository.incrementUsageIfUnderLimit(coupon.getId()) == 0) {
            LOG.warn("Coupon {} usage limit reached, rejecting user {}", coupon.getCode(), userId);
            throw new CouponUsageLimitReachedException(coupon.getCode());
        }
        return usage;
    }

    /** Rejects a missing code before it reaches the lookup, instead of a null-pointer failure. */
    private void validateCode(String code) {
        if (code == null || code.isBlank()) {
            throw new InvalidCouponRequestException("Coupon code must be non-empty");
        }
    }

    /**
     * Rejects a missing or oversized user ID before it reaches the database — a NOT NULL or
     * length violation on {@code coupon_usage.user_id} would otherwise raise the same exception
     * type the unique-constraint check in {@link #registerUsage} relies on, and get
     * misclassified as an already-used conflict instead of a bad request.
     */
    private void validateUserId(String userId) {
        if (userId == null || userId.isBlank() || userId.length() > MAX_USER_ID_LENGTH) {
            throw new InvalidCouponRequestException(
                    "User ID must be non-empty and at most " + MAX_USER_ID_LENGTH + " characters");
        }
    }
}
