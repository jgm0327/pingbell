create table runbook_documents (
    id bigserial primary key,
    tenant_id bigint not null,
    owner_id bigint not null,
    document_id varchar(100) not null,
    constraint uk_runbook_document_tenant_document unique (tenant_id, document_id)
);

create table runbook_revisions (
    id bigserial primary key,
    runbook_document_id bigint not null,
    title varchar(200) not null,
    document_type varchar(30) not null,
    service_name varchar(100) not null,
    version integer not null check (version > 0),
    status varchar(30) not null check (status in ('DRAFT', 'ACTIVE', 'SUPERSEDED', 'RETIRED')),
    content text not null,
    updated_at timestamp with time zone not null,
    constraint uk_runbook_revision_document_version unique (runbook_document_id, version)
);

create table runbook_revision_error_types (
    runbook_revision_id bigint not null,
    sort_order integer not null,
    error_type varchar(100) not null,
    primary key (runbook_revision_id, sort_order)
);

create unique index uk_runbook_revision_single_active
    on runbook_revisions(runbook_document_id)
    where status = 'ACTIVE';

create index idx_runbook_document_tenant
    on runbook_documents(tenant_id, id);

create index idx_runbook_revision_active_updated
    on runbook_revisions(runbook_document_id, updated_at desc)
    where status = 'ACTIVE';
