CREATE TABLE monitors (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES member(id),
    name VARCHAR(100) NOT NULL,
    url VARCHAR(2048) NOT NULL,
    interval_seconds INTEGER NOT NULL,
    timeout_millis INTEGER NOT NULL,
    failure_threshold INTEGER NOT NULL,
    recovery_threshold INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    next_check_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

CREATE INDEX idx_monitors_user_id ON monitors(user_id);
CREATE INDEX idx_monitors_next_check_at ON monitors(next_check_at);
