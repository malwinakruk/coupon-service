package com.empik.couponservice.service;

import com.empik.couponservice.client.GeoLocationService;
import com.empik.couponservice.entity.Coupon;
import com.empik.couponservice.entity.CouponUsage;
import com.empik.couponservice.exception.CountryNotAllowedException;
import com.empik.couponservice.exception.CouponAlreadyUsedException;
import com.empik.couponservice.exception.CouponNotFoundException;
import com.empik.couponservice.exception.CouponUsageLimitReachedException;
import com.empik.couponservice.repository.CouponRepository;
import com.empik.couponservice.repository.CouponUsageRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Default {@link CouponUsageService} implementation.
 */
@Service
class CouponUsageServiceImpl implements CouponUsageService {

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
        Coupon coupon =
                couponRepository.findByCode(code.toLowerCase()).orElseThrow(() -> new CouponNotFoundException(code));

        String userCountry = geoLocationService.lookupCountry(ipAddress);
        if (!userCountry.equals(coupon.getCountry())) {
            throw new CountryNotAllowedException(userCountry, coupon.getCountry());
        }

        return usageTransaction.execute(status -> registerUsage(coupon, userId));
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
            throw new CouponAlreadyUsedException(coupon.getCode(), userId);
        }

        if (couponRepository.incrementUsageIfUnderLimit(coupon.getId()) == 0) {
            throw new CouponUsageLimitReachedException(coupon.getCode());
        }
        return usage;
    }
}
