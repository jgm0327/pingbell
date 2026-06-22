alter table notification_histories
    add column max_retry_count int not null default 2,
    add column next_retry_at timestamp,
    add column last_attempted_at timestamp,
    add column retryable boolean not null default false;

create index idx_notification_histories_retry_pending
    on notification_histories(status, next_retry_at);
