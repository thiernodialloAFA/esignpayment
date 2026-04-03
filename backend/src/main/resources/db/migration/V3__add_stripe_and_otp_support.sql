-- V3: Add Stripe payment integration and SMS OTP support for e-signature

-- Add Stripe PaymentIntent ID to payments table
ALTER TABLE payments ADD COLUMN stripe_payment_intent_id VARCHAR(255);
CREATE INDEX IF NOT EXISTS idx_payments_stripe_pi ON payments(stripe_payment_intent_id);

-- Add phone number to document_signers for SMS OTP verification
ALTER TABLE document_signers ADD COLUMN phone VARCHAR(20);

-- Add OTP fields to document_signers for signature verification
ALTER TABLE document_signers ADD COLUMN otp_code VARCHAR(6);
ALTER TABLE document_signers ADD COLUMN otp_expires_at TIMESTAMP;
ALTER TABLE document_signers ADD COLUMN otp_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE document_signers ADD COLUMN otp_attempts INTEGER NOT NULL DEFAULT 0;
