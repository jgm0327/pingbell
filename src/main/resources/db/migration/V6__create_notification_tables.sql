create table notification_channels (
    id bigserial primary key,
    public_id uuid not null unique,
    member_id bigint not null,
    type varchar(30) not null,
    target varchar(500) not null,
    enabled boolean not null,
    created_at timestamp not null,
    updated_at timestamp
);

create index idx_notification_channels_member_id
    on notification_channels(member_id);

create table notification_histories (
    id bigserial primary key,
    incident_id bigint not null,
    channel_id bigint not null,
    notification_type varchar(30) not null,
    status varchar(20) not null,
    retry_count int not null,
    error_message varchar(1000),
    sent_at timestamp,
    created_at timestamp not null,
    updated_at timestamp
);

create index idx_notification_histories_incident_id
    on notification_histories(incident_id);

create index idx_notification_histories_channel_id
    on notification_histories(channel_id);
