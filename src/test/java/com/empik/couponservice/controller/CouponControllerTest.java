package com.empik.couponservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.empik.couponservice.entity.Coupon;
import com.empik.couponservice.exception.CouponCodeConflictException;
import com.empik.couponservice.exception.InvalidCouponRequestException;
import com.empik.couponservice.service.CouponCreationResult;
import com.empik.couponservice.service.CouponService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code @WebMvcTest} slice for {@link CouponController}, verifying the HTTP status and error
 * shape for each of UC1's variants. {@link CouponService} is mocked — this layer only checks
 * request/response mapping, not the business logic behind it.
 */
@WebMvcTest(CouponController.class)
class CouponControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CouponService couponService;

    /** Variant A: a new coupon is created, returning 201 with its data. */
    @Test
    void createsNewCoupon() throws Exception {
        Coupon coupon = new Coupon("spring", 10, "PL");
        when(couponService.createCoupon(eq("SPRING"), eq(10), eq("pl")))
                .thenReturn(new CouponCreationResult(coupon, true));

        mockMvc.perform(post("/coupons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Request("SPRING", 10, "pl"))))
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
                        .content(objectMapper.writeValueAsString(new Request("summer", 5, "PL"))))
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
                        .content(objectMapper.writeValueAsString(new Request("bad code", 5, "PL"))))
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
                        .content(objectMapper.writeValueAsString(new Request("autumn", 5, "PL"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CODE_ALREADY_EXISTS"));
    }

    private record Request(String code, Integer maxUses, String country) {
    }
}
