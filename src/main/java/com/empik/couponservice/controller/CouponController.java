package com.empik.couponservice.controller;

import com.empik.couponservice.dto.CouponCreationRequest;
import com.empik.couponservice.dto.CouponResponse;
import com.empik.couponservice.service.CouponCreationResult;
import com.empik.couponservice.service.CouponService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint for creating coupons (UC1).
 */
@RestController
@RequestMapping("/coupons")
class CouponController {

    private final CouponService couponService;

    /**
     * Creates the controller with its service dependency.
     *
     * @param couponService service used to create coupons
     */
    CouponController(CouponService couponService) {
        this.couponService = couponService;
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
}
