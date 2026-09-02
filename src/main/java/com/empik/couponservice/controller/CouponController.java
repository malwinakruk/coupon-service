package com.empik.couponservice.controller;

import com.empik.couponservice.dto.CouponCreationRequest;
import com.empik.couponservice.dto.CouponResponse;
import com.empik.couponservice.dto.CouponUsageRequest;
import com.empik.couponservice.dto.CouponUsageResponse;
import com.empik.couponservice.entity.CouponUsage;
import com.empik.couponservice.service.CouponCreationResult;
import com.empik.couponservice.service.CouponService;
import com.empik.couponservice.service.CouponUsageService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for creating (UC1) and redeeming (UC2) coupons.
 */
@RestController
@RequestMapping("/coupons")
class CouponController {

    private final CouponService couponService;
    private final CouponUsageService couponUsageService;

    /**
     * Creates the controller with its service dependencies.
     *
     * @param couponService service used to create coupons
     * @param couponUsageService service used to redeem coupons
     */
    CouponController(CouponService couponService, CouponUsageService couponUsageService) {
        this.couponService = couponService;
        this.couponUsageService = couponUsageService;
    }

    /**
     * Creates a coupon, or returns the existing one if this is the same request retried.
     *
     * @param request coupon code, usage limit, and country to create
     * @return {@code 201} with the new coupon, or {@code 200} with the existing one if this is
     *     an identical request retried
     */
    @PostMapping
    ResponseEntity<CouponResponse> createCoupon(@RequestBody CouponCreationRequest request) {
        CouponCreationResult result = couponService.createCoupon(request.code(), request.maxUses(), request.country());
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(CouponResponse.from(result.coupon()));
    }

    /**
     * Redeems a coupon for the requesting user, geolocating them from the request's IP address.
     *
     * @param code coupon code to redeem
     * @param request the redeeming user's identifier
     * @param httpRequest used to read the caller's IP address
     * @return {@code 200} confirming the usage
     */
    @PostMapping("/redeem")
    ResponseEntity<CouponUsageResponse> useCoupon(
            @RequestParam String code, @RequestBody CouponUsageRequest request, HttpServletRequest httpRequest) {
        CouponUsage usage = couponUsageService.useCoupon(code, request.userId(), httpRequest.getRemoteAddr());
        return ResponseEntity.ok(CouponUsageResponse.from(usage));
    }
}
