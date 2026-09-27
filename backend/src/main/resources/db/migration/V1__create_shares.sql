CREATE TABLE shares (
    id UUID PRIMARY KEY,
    share_token VARCHAR(32) NOT NULL,
    type VARCHAR(16) NOT NULL,
    text_content TEXT,
    original_filename VARCHAR(255),
    content_type VARCHAR(127),
    file_size BIGINT,
    storage_key VARCHAR(128),
    password_hash VARCHAR(100),
    management_token_hash CHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT uk_shares_share_token UNIQUE (share_token),
    CONSTRAINT ck_shares_type CHECK (type IN ('TEXT', 'FILE'))
);

CREATE INDEX idx_shares_expires_at ON shares (expires_at);
