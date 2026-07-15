create table incident_detection_processed_check_results (
    id bigserial primary key,
    check_result_id bigint not null,
    created_at timestamp not null,
    updated_at timestamp
);

create unique index uk_incident_detection_processed_check_result
    on incident_detection_processed_check_results(check_result_id);
