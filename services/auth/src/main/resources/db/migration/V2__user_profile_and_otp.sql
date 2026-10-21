-- DETE Auth Service: User Profile & Email OTP Schema Evolution

ALTER TABLE auth.users
    ADD COLUMN IF NOT EXISTS full_name VARCHAR(128),
    ADD COLUMN IF NOT EXISTS date_of_birth DATE,
    ADD COLUMN IF NOT EXISTS role VARCHAR(32) NOT NULL DEFAULT 'ROLE_TRADER',
    ADD COLUMN IF NOT EXISTS is_verified BOOLEAN NOT NULL DEFAULT true;

-- Update existing demo user with profile details
UPDATE auth.users
SET full_name = 'Demo Trader',
    role = 'ROLE_TRADER',
    is_verified = true
WHERE username = 'demo' AND (full_name IS NULL OR role IS NULL);

-- Email OTP verification table for registrations
CREATE TABLE IF NOT EXISTS auth.email_otps (
    otp_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(256) NOT NULL,
    otp_code      VARCHAR(6) NOT NULL,
    full_name     VARCHAR(128),
    date_of_birth DATE,
    password_hash VARCHAR(256) NOT NULL,
    expires_at    TIMESTAMPTZ NOT NULL,
    consumed      BOOLEAN NOT NULL DEFAULT false,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_email_otps_lookup ON auth.email_otps(email, otp_code, consumed, expires_at);
CREATE INDEX IF NOT EXISTS idx_email_otps_email ON auth.email_otps(email);
