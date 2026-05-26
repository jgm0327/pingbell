CREATE TABLE check_results (
    id BIGSERIAL PRIMARY KEY,
    monitor_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    http_status INTEGER,
    response_time_ms BIGINT NOT NULL,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

CREATE INDEX idx_check_results_monitor_id ON check_results(monitor_id);
CREATE INDEX idx_check_results_created_at ON check_results(created_at);
