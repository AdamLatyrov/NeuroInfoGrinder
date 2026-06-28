create table guide_publication_settings (
    id bigserial primary key,
    enabled boolean not null default false,
    mode varchar(32) not null default 'TDLIB_ACCOUNT',
    target_group_id bigint,
    target_telegram_chat_id bigint,
    target_topic_id bigint,
    append_source_link boolean not null default true,
    min_confidence double precision not null default 0.85,
    send_only_statuses varchar(256) not null default 'DRAFT,APPROVED',
    format varchar(32) not null default 'PLAIN_TEXT',
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now()
);

create table guide_publication_log (
    id bigserial primary key,
    guide_id bigint not null references guides(id) on delete cascade,
    target_mode varchar(32) not null,
    target_group_id bigint,
    target_telegram_chat_id bigint,
    target_topic_id bigint,
    status varchar(16) not null,
    telegram_message_id bigint,
    error text,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now(),
    sent_at timestamp with time zone
);

create index idx_guide_publication_log_guide_id_created_at
    on guide_publication_log (guide_id, created_at desc);

create index idx_guide_publication_log_status
    on guide_publication_log (status);
