create index idx_check_results_monitor_id_id_desc
    on check_results(monitor_id, id desc);

create index idx_check_results_monitor_created_at_desc
    on check_results(monitor_id, created_at desc);
