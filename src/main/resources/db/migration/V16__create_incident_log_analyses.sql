create table incident_log_analyses (
    id bigserial primary key,
    incident_id bigint not null,
    status varchar(20) not null,
    result_json text,
    error_message varchar(1000),
    requested_at timestamp not null,
    completed_at timestamp,
    created_at timestamp not null,
    updated_at timestamp,
    constraint uk_incident_log_analysis_incident unique (incident_id)
);
