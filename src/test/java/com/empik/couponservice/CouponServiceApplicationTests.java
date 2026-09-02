package com.empik.couponservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Smoke test verifying the Spring application context boots against a real Postgres instance.
 */
@SpringBootTest
@Testcontainers
class CouponServiceApplicationTests {

    /** Postgres instance the application connects to for this test run. */
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    /** Verifies the Spring application context starts successfully. */
    @Test
    void contextLoads() {
    }
}
