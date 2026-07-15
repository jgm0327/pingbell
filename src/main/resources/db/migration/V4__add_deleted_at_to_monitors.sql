ALTER TABLE monitors
    ADD COLUMN deleted_at TIMESTAMP;

CREATE INDEX idx_monitors_due_active
    ON monitors(next_check_at)
    WHERE deleted_at IS NULL;
