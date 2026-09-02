package com.empik.couponservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * A coupon: a code, a usage limit, and a country restriction.
 *
 * <p>{@code currentUses} has no setter — it is only ever changed via the atomic
 * {@code UPDATE ... WHERE} in the repository layer, never through this entity. Loading the
 * value into memory and saving it back would reopen the exact race condition that mechanism
 * exists to close.
 */
@Entity
@Table(name = "coupons")
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "max_uses", nullable = false)
    private Integer maxUses;

    @Column(name = "current_uses", nullable = false)
    private Integer currentUses;

    @Column(nullable = false, length = 2)
    private String country;

    /** Required by JPA. */
    protected Coupon() {
    }

    /**
     * Creates a new coupon with zero uses so far.
     *
     * @param code lowercase-normalized coupon code
     * @param maxUses maximum number of allowed uses
     * @param country ISO 3166-1 alpha-2 country this coupon is restricted to
     */
    public Coupon(String code, Integer maxUses, String country) {
        this.code = code;
        this.createdAt = OffsetDateTime.now();
        this.maxUses = maxUses;
        this.currentUses = 0;
        this.country = country;
    }

    /** @return the surrogate identifier */
    public Long getId() {
        return id;
    }

    /** @return the lowercase-normalized coupon code */
    public String getCode() {
        return code;
    }

    /** @return when the coupon was created */
    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    /** @return the maximum number of allowed uses */
    public Integer getMaxUses() {
        return maxUses;
    }

    /** @return the current number of uses */
    public Integer getCurrentUses() {
        return currentUses;
    }

    /** @return the ISO 3166-1 alpha-2 country this coupon is restricted to */
    public String getCountry() {
        return country;
    }
}
