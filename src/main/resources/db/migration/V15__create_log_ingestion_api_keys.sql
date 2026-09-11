create table log_ingestion_api_keys (
    id bigserial primary key,
    monitor_id bigint not null,
    tenant_id bigint not null,
    key_hash varchar(64) not null,
    key_prefix varchar(20) not null,
    created_at timestamp not null,
    updated_at timestamp,
    last_used_at timestamp,
    revoked_at timestamp,
    constraint uk_log_ingestion_api_key_hash unique (key_hash)
);

create index idx_log_ingestion_api_key_tenant_monitor
    on log_ingestion_api_keys(tenant_id, monitor_id);
