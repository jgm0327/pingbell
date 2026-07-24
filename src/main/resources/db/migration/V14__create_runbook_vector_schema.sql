do $$
begin
    if not exists (
        select 1
        from pg_available_extensions
        where name = 'vector'
    ) then
        raise exception using
            errcode = '0A000',
            message = 'PGVECTOR_EXTENSION_NOT_AVAILABLE: install pgvector for this PostgreSQL server before running Flyway V14';
    end if;
end
$$;

create extension if not exists vector with schema public;

create table runbook_chunks (
    id bigserial primary key,
    tenant_id bigint not null,
    document_id varchar(100) not null,
    version integer not null check (version > 0),
    chunk_id varchar(160) not null,
    chunk_order integer not null check (chunk_order >= 0),
    section_title varchar(200) not null,
    content text not null check (length(content) > 0),
    content_hash char(64) not null,
    created_at timestamp with time zone not null,
    constraint uk_runbook_chunk_boundary_id
        unique (tenant_id, document_id, version, chunk_id),
    constraint uk_runbook_chunk_boundary_order
        unique (tenant_id, document_id, version, chunk_order)
);

create table runbook_chunk_embeddings (
    id bigserial primary key,
    tenant_id bigint not null,
    document_id varchar(100) not null,
    version integer not null check (version > 0),
    chunk_id varchar(160) not null,
    embedding public.vector not null,
    model_name varchar(150) not null,
    embedding_dimension integer not null check (embedding_dimension between 1 and 16000),
    generated_at timestamp with time zone not null,
    active boolean not null default true,
    constraint ck_runbook_chunk_embedding_dimension
        check (public.vector_dims(embedding) = embedding_dimension)
);

create index idx_runbook_chunk_tenant_document_version
    on runbook_chunks(tenant_id, document_id, version, chunk_order);

create index idx_runbook_embedding_tenant_document_version
    on runbook_chunk_embeddings(tenant_id, document_id, version, active);

create unique index uk_runbook_embedding_single_active_chunk
    on runbook_chunk_embeddings(tenant_id, document_id, version, chunk_id)
    where active = true;
