-- V1: Initial schema for ESign Payment application

-- Users table
CREATE TABLE IF NOT EXISTS users (
    id          UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    email       VARCHAR(255) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    first_name  VARCHAR(100) NOT NULL,
    last_name   VARCHAR(100) NOT NULL,
    role        VARCHAR(50)  NOT NULL DEFAULT 'ROLE_USER',
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);

-- Documents table
CREATE TABLE IF NOT EXISTS documents (
    id           UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    title        VARCHAR(255) NOT NULL,
    description  TEXT,
    file_path    VARCHAR(512) NOT NULL,
    file_name    VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    status       VARCHAR(50)  NOT NULL DEFAULT 'DRAFT',
    owner_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_documents_owner_id ON documents(owner_id);
CREATE INDEX IF NOT EXISTS idx_documents_status   ON documents(status);

-- Document signers table
CREATE TABLE IF NOT EXISTS document_signers (
    id               UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    document_id      UUID         NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    email            VARCHAR(255) NOT NULL,
    name             VARCHAR(255) NOT NULL,
    status           VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    signature_token  VARCHAR(255) UNIQUE,
    signed_at        TIMESTAMP,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_document_signers_document_id     ON document_signers(document_id);
CREATE INDEX IF NOT EXISTS idx_document_signers_email           ON document_signers(email);
CREATE INDEX IF NOT EXISTS idx_document_signers_signature_token ON document_signers(signature_token);

-- Signatures table
CREATE TABLE IF NOT EXISTS signatures (
    id             UUID      NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    document_id    UUID      NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    signer_id      UUID      NOT NULL REFERENCES document_signers(id) ON DELETE CASCADE,
    signature_data TEXT      NOT NULL,
    ip_address     VARCHAR(50),
    signed_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_signatures_document_id ON signatures(document_id);
CREATE INDEX IF NOT EXISTS idx_signatures_signer_id   ON signatures(signer_id);

-- Payments table
CREATE TABLE IF NOT EXISTS payments (
    id                  UUID           NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id             UUID           NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    amount              NUMERIC(19, 4) NOT NULL,
    currency            VARCHAR(3)     NOT NULL DEFAULT 'EUR',
    status              VARCHAR(50)    NOT NULL DEFAULT 'PENDING',
    description         TEXT,
    provider_reference  VARCHAR(255),
    created_at          TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_payments_user_id ON payments(user_id);
CREATE INDEX IF NOT EXISTS idx_payments_status  ON payments(status);
