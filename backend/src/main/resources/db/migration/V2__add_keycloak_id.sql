-- V2: Add keycloak_id column and remove password requirement for Keycloak integration

ALTER TABLE users ADD COLUMN keycloak_id VARCHAR(255);

-- Make password nullable since auth is now via Keycloak
ALTER TABLE users ALTER COLUMN password DROP NOT NULL;

-- Create unique index on keycloak_id
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_keycloak_id ON users(keycloak_id);
