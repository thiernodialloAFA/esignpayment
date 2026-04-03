-- V4: Bank Account Opening module

-- Account types reference table
CREATE TABLE IF NOT EXISTS account_types (
    id          UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    code        VARCHAR(50)  NOT NULL UNIQUE,
    label       VARCHAR(100) NOT NULL,
    description TEXT,
    monthly_fee NUMERIC(10,2) NOT NULL DEFAULT 0,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

INSERT INTO account_types (code, label, description, monthly_fee) VALUES
    ('CHECKING', 'Compte Courant', 'Compte courant pour les opérations quotidiennes', 0),
    ('SAVINGS', 'Compte Épargne', 'Compte épargne avec taux d''intérêt avantageux', 0),
    ('PREMIUM', 'Compte Premium', 'Compte premium avec services exclusifs et carte Gold', 9.90);

-- Account applications
CREATE TABLE IF NOT EXISTS account_applications (
    id                    UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id               UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    account_type_id       UUID         NOT NULL REFERENCES account_types(id),
    reference_number      VARCHAR(50)  NOT NULL UNIQUE,
    status                VARCHAR(50)  NOT NULL DEFAULT 'DRAFT',
    date_of_birth         DATE,
    phone_number          VARCHAR(20),
    nationality           VARCHAR(100),
    address_line1         VARCHAR(255),
    address_line2         VARCHAR(255),
    city                  VARCHAR(100),
    postal_code           VARCHAR(20),
    country               VARCHAR(100),
    employment_status     VARCHAR(50),
    employer_name         VARCHAR(255),
    job_title             VARCHAR(255),
    monthly_income        NUMERIC(12,2),
    contract_document_id  UUID         REFERENCES documents(id),
    submitted_at          TIMESTAMP,
    approved_at           TIMESTAMP,
    created_at            TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_account_applications_user_id ON account_applications(user_id);
CREATE INDEX IF NOT EXISTS idx_account_applications_status ON account_applications(status);
CREATE INDEX IF NOT EXISTS idx_account_applications_ref ON account_applications(reference_number);

-- KYC documents
CREATE TABLE IF NOT EXISTS kyc_documents (
    id               UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    application_id   UUID         NOT NULL REFERENCES account_applications(id) ON DELETE CASCADE,
    document_type    VARCHAR(50)  NOT NULL,
    file_name        VARCHAR(255) NOT NULL,
    file_path        VARCHAR(512) NOT NULL,
    content_type     VARCHAR(100) NOT NULL,
    status           VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    rejection_reason TEXT,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_kyc_documents_application_id ON kyc_documents(application_id);

-- Application status history
CREATE TABLE IF NOT EXISTS application_status_history (
    id              UUID      NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    application_id  UUID      NOT NULL REFERENCES account_applications(id) ON DELETE CASCADE,
    from_status     VARCHAR(50),
    to_status       VARCHAR(50) NOT NULL,
    changed_by      UUID      REFERENCES users(id),
    comment         TEXT,
    changed_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_status_history_application_id ON application_status_history(application_id);

