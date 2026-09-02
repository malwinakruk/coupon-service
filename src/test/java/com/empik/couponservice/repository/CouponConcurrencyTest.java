package com.empik.couponservice.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.empik.couponservice.entity.Coupon;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies ADR-4's atomic concurrency mechanism (4.1.3): under concurrent load on the same
 * coupon, exactly {@code max_uses} increments succeed and the rest are rejected — never more.
 * Real Postgres (Testcontainers), real concurrent threads, real committed transactions.
 */
@DataJpaTest(properties = "spring.datasource.hikari.maximum-pool-size=20")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class CouponConcurrencyTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /**
     * Fires more concurrent increment attempts than the coupon's limit allows; asserts exactly
     * {@code maxUses} succeed and the stored count never exceeds it (NFR1).
     *
     * <p>Runs outside the test's default transaction ({@code Propagation.NOT_SUPPORTED}) so each
     * thread commits its own transaction — real cross-thread visibility, not one shared connection.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void onlyMaxUsesConcurrentIncrementsSucceed() throws InterruptedException {
        int maxUses = 5;
        int attempts = 20;
        Long couponId = couponRepository.saveAndFlush(new Coupon("concurrency-test", maxUses, "PL")).getId();

        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        for (int i = 0; i < attempts; i++) {
            executor.submit(() -> {
                ready.countDown();
                awaitUninterruptibly(start);
                Integer updated = transactionTemplate.execute(
                        status -> couponRepository.incrementUsageIfUnderLimit(couponId));
                if (updated != null && updated > 0) {
                    successCount.incrementAndGet();
                }
            });
        }

        ready.await();
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(successCount.get()).isEqualTo(maxUses);
        assertThat(couponRepository.findById(couponId).orElseThrow().getCurrentUses()).isEqualTo(maxUses);
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
