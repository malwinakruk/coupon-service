package com.empik.couponservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.empik.couponservice.entity.Coupon;
import com.empik.couponservice.entity.CouponUsage;
import com.empik.couponservice.exception.CountryNotAllowedException;
import com.empik.couponservice.exception.CouponAlreadyUsedException;
import com.empik.couponservice.exception.CouponCodeConflictException;
import com.empik.couponservice.exception.CouponNotFoundException;
import com.empik.couponservice.exception.CouponUsageLimitReachedException;
import com.empik.couponservice.exception.GeoLocationUnavailableException;
import com.empik.couponservice.exception.InvalidCouponRequestException;
import com.empik.couponservice.service.CouponCreationResult;
import com.empik.couponservice.service.CouponService;
import com.empik.couponservice.service.CouponUsageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code @WebMvcTest} slice for {@link CouponController}, verifying the HTTP status and error
 * shape for each of UC1's and UC2's variants. {@link CouponService} and {@link CouponUsageService}
 * are mocked — this layer only checks request/response mapping, not the business logic behind it.
 */
@WebMvcTest(CouponController.class)
class CouponControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CouponService couponService;

    @MockitoBean
    private CouponUsageService couponUsageService;

    /** Variant A: a new coupon is created, returning 201 with its data. */
    @Test
    void createsNewCoupon() throws Exception {
        Coupon coupon = new Coupon("spring", 10, "PL");
        when(couponService.createCoupon(eq("SPRING"), eq(10), eq("pl")))
                .thenReturn(new CouponCreationResult(coupon, true));

        mockMvc.perform(post("/coupons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreationRequest("SPRING", 10, "pl"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("spring"))
                .andExpect(jsonPath("$.maxUses").value(10))
                .andExpect(jsonPath("$.country").value("PL"));
    }

    /** Variant D: an identical request retried returns 200 with the existing coupon. */
    @Test
    void retriedRequestReturnsOk() throws Exception {
        Coupon coupon = new Coupon("summer", 5, "PL");
        when(couponService.createCoupon(eq("summer"), eq(5), eq("PL")))
                .thenReturn(new CouponCreationResult(coupon, false));

        mockMvc.perform(post("/coupons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreationRequest("summer", 5, "PL"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("summer"));
    }

    /** Variant B: invalid input surfaces as 400 with the INVALID_REQUEST error code. */
    @Test
    void invalidRequestReturnsBadRequest() throws Exception {
        when(couponService.createCoupon(any(), any(), any()))
                .thenThrow(new InvalidCouponRequestException("bad code"));

        mockMvc.perform(post("/coupons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreationRequest("bad code", 5, "PL"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("bad code"));
    }

    /** Variant C: a genuine conflict surfaces as 409 with the CODE_ALREADY_EXISTS error code. */
    @Test
    void conflictReturnsConflict() throws Exception {
        when(couponService.createCoupon(any(), any(), any()))
                .thenThrow(new CouponCodeConflictException("autumn"));

        mockMvc.perform(post("/coupons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreationRequest("autumn", 5, "PL"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CODE_ALREADY_EXISTS"));
    }

    /** UC2 Variant A: a successful redemption returns 200 with the usage's data. */
    @Test
    void redeemsCoupon() throws Exception {
        CouponUsage usage = new CouponUsage(new Coupon("golden", 5, "PL"), "user-1");
        when(couponUsageService.useCoupon(eq("golden"), eq("user-1"), any())).thenReturn(usage);

        mockMvc.perform(post("/coupons/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UsageRequest("golden", "user-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("golden"))
                .andExpect(jsonPath("$.userId").value("user-1"));
    }

    /** UC2 Variant B: an unknown code surfaces as 404 with the COUPON_NOT_FOUND error code. */
    @Test
    void missingCouponReturnsNotFound() throws Exception {
        when(couponUsageService.useCoupon(any(), any(), any())).thenThrow(new CouponNotFoundException("missing"));

        mockMvc.perform(post("/coupons/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UsageRequest("missing", "user-1"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("COUPON_NOT_FOUND"));
    }

    /** UC2 Variant C: a geolocation failure surfaces as 503 with the GEO_UNAVAILABLE error code. */
    @Test
    void geoLocationFailureReturnsServiceUnavailable() throws Exception {
        when(couponUsageService.useCoupon(any(), any(), any()))
                .thenThrow(new GeoLocationUnavailableException("1.2.3.4", null));

        mockMvc.perform(post("/coupons/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UsageRequest("golden", "user-1"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("GEO_UNAVAILABLE"));
    }

    /** UC2 Variant D: a country mismatch surfaces as 403 with the COUNTRY_NOT_ALLOWED error code. */
    @Test
    void wrongCountryReturnsForbidden() throws Exception {
        when(couponUsageService.useCoupon(any(), any(), any())).thenThrow(new CountryNotAllowedException("DE", "PL"));

        mockMvc.perform(post("/coupons/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UsageRequest("golden", "user-1"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("COUNTRY_NOT_ALLOWED"));
    }

    /** UC2 Variant E: a repeat use surfaces as 409 with the ALREADY_USED error code. */
    @Test
    void repeatUseReturnsConflict() throws Exception {
        when(couponUsageService.useCoupon(any(), any(), any()))
                .thenThrow(new CouponAlreadyUsedException("golden", "user-1"));

        mockMvc.perform(post("/coupons/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UsageRequest("golden", "user-1"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ALREADY_USED"));
    }

    /** UC2 Variant F: a coupon at its limit surfaces as 409 with the LIMIT_REACHED error code. */
    @Test
    void limitReachedReturnsConflict() throws Exception {
        when(couponUsageService.useCoupon(any(), any(), any()))
                .thenThrow(new CouponUsageLimitReachedException("golden"));

        mockMvc.perform(post("/coupons/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UsageRequest("golden", "user-1"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("LIMIT_REACHED"));
    }

    private record CreationRequest(String code, Integer maxUses, String country) {
    }

    private record UsageRequest(String code, String userId) {
    }
}
