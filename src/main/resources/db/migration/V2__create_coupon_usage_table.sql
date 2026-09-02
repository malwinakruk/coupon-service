-- Successful coupon redemptions; denied attempts are never inserted here.
CREATE TABLE coupon_usage (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,  -- identifier
    coupon_id   BIGINT NOT NULL REFERENCES coupons (id),          -- which coupon was used
    user_id     VARCHAR(255) NOT NULL,                            -- caller-supplied user identifier
    used_at     TIMESTAMPTZ NOT NULL DEFAULT now(),               -- when the usage was registered
    UNIQUE (coupon_id, user_id)                                   -- one use per user per coupon, enforced even if two requests race
);
