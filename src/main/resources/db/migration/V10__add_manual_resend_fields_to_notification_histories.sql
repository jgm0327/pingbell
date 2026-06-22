alter table notification_histories
    add column manual_resend boolean not null default false,
    add column resend_of_history_id bigint;

create index idx_notification_histories_resend_of_history_id
    on notification_histories(resend_of_history_id);
