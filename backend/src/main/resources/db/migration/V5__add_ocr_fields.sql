-- V5: Add OCR verification fields to kyc_documents
ALTER TABLE kyc_documents
    ADD COLUMN extracted_text       TEXT,
    ADD COLUMN ocr_status           VARCHAR(50)  DEFAULT 'PENDING',
    ADD COLUMN ocr_match_score      INTEGER,
    ADD COLUMN ocr_details          TEXT,
    ADD COLUMN document_type_valid  BOOLEAN      DEFAULT FALSE,
    ADD COLUMN ocr_warnings         TEXT;

