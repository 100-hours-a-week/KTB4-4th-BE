CREATE TABLE IF NOT EXISTS auth_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    refresh_token_hash VARBINARY(32) NOT NULL,
    device_name VARCHAR(100),
    ip_address VARBINARY(16),
    refresh_expires_at DATETIME(6) NOT NULL,
    last_used_at DATETIME(6),
    revoked_at DATETIME(6),
    revoke_reason VARCHAR(30),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_auth_sessions_refresh_token_hash (refresh_token_hash),
    KEY idx_auth_sessions_user_id (user_id),
    CONSTRAINT fk_auth_sessions_user_id FOREIGN KEY (user_id) REFERENCES users (id)
);
