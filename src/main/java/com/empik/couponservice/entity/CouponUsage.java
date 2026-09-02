package com.empik.couponservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;

/**
 * A single successful registration of a coupon being used by a specific user.
 *
 * <p>The unique constraint on ({@code coupon_id}, {@code user_id}) is what enforces one use
 * per user per coupon (ADR-4) — a repeat insert fails immediately with a constraint violation.
 */
@Entity
@Table(
        name = "coupon_usage",
        uniqueConstraints = @UniqueConstraint(columnNames = {"coupon_id", "user_id"}))
public class CouponUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "coupon_id", nullable = false)
    private Coupon coupon;

    @Column(name = "user_id", nullable = false, length = 255)
    private String userId;

    @Column(name = "used_at", nullable = false)
    private OffsetDateTime usedAt;

    /** Required by JPA. */
    protected CouponUsage() {
    }

    /**
     * Registers a new usage of the given coupon by the given user, timestamped now.
     *
     * @param coupon the coupon being used
     * @param userId arbitrary caller-supplied identifier of the user
     */
    public CouponUsage(Coupon coupon, String userId) {
        this.coupon = coupon;
        this.userId = userId;
        this.usedAt = OffsetDateTime.now();
    }

    /**
     * Returns the surrogate identifier.
     *
     * @return the identifier
     */
    public Long getId() {
        return id;
    }

    /**
     * Returns the coupon this usage refers to.
     *
     * @return the coupon
     */
    public Coupon getCoupon() {
        return coupon;
    }

    /**
     * Returns the identifier of the user who used the coupon.
     *
     * @return the user identifier
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Returns when the usage was registered.
     *
     * @return the usage timestamp
     */
    public OffsetDateTime getUsedAt() {
        return usedAt;
    }
}
