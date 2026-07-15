alter table monitors add column failure_count int not null default 0;
alter table monitors add column recovery_count int not null default 0;

create table incidents(
    id bigserial primary key,
    monitor_id bigint not null,
    status varchar(8) not null,
    started_at timestamp not null,
    resolved_at timestamp,
    created_at timestamp not null default now(),
    updated_at timestamp,
    last_error_message varchar(1000)
);

create unique index uk_incidents_one_open_per_monitor
    on incidents(monitor_id)
    where status = 'OPEN';