CREATE TABLE shares (
    id UUID PRIMARY KEY,
    name VARCHAR(63) NOT NULL,
    type VARCHAR(16) NOT NULL,
    text_content TEXT,
    original_filename VARCHAR(255),
    extension VARCHAR(16),
    content_type VARCHAR(127),
    file_size BIGINT,
    storage_key VARCHAR(64),
    password_hash VARCHAR(100),
    management_token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT uk_shares_name UNIQUE (name),
    CONSTRAINT ck_shares_type CHECK (type IN ('TEXT', 'FILE'))
);

CREATE INDEX idx_shares_expires_at ON shares (expires_at);
