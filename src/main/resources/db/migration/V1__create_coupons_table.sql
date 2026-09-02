-- Coupon catalog: one row per coupon code.
CREATE TABLE coupons (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,  -- identifier
    code          VARCHAR(64) NOT NULL UNIQUE,                      -- lowercase-normalized, so casing can't create duplicates
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),               -- creation timestamp
    max_uses      INTEGER NOT NULL CHECK (max_uses > 0),            -- usage limit, must be positive
    current_uses  INTEGER NOT NULL DEFAULT 0
                  CHECK (current_uses BETWEEN 0 AND max_uses),      -- DB-enforced so it can never exceed max_uses, even under concurrent writes
    country       CHAR(2) NOT NULL                                  -- ISO 3166-1 alpha-2 country restriction
);
