-- V6: Add signed_file_path to documents for storing the signed version of contracts
ALTER TABLE documents
    ADD COLUMN signed_file_path VARCHAR(500);

