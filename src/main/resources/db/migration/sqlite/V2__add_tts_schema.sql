create table if not exists tts_audio_seq (
    next_val bigint
);
insert into tts_audio_seq (next_val)
select 1 where not exists (select 1 from tts_audio_seq);

create table if not exists tts_preset_seq (
    next_val bigint
);
insert into tts_preset_seq (next_val)
select 1 where not exists (select 1 from tts_preset_seq);

alter table deck add column tts_enabled boolean not null default false;
alter table deck add column tts_model_id varchar(255);
alter table deck add column tts_default_preset_id bigint;

alter table card add column tts_text varchar(32600);
alter table card add column tts_preset_id bigint;
alter table card add column tts_display_side varchar(255);

create table if not exists tts_preset (
    id bigint not null,
    advanced_config_json varchar(32600),
    caption varchar(32600),
    language varchar(255),
    name varchar(255) not null,
    sort_order integer not null,
    speaker varchar(255),
    deck_id bigint not null,
    primary key (id)
);

create table if not exists tts_audio (
    id bigint not null,
    cache_key varchar(255) not null,
    content_type varchar(255) not null,
    file_path varchar(32600) not null,
    generated_at bigint not null,
    model_id varchar(255) not null,
    preset_config_json varchar(32600),
    preset_name varchar(255),
    card_id bigint not null,
    deck_id bigint not null,
    preset_id bigint,
    primary key (id),
    constraint uk_tts_audio_card_cache unique (card_id, cache_key)
);
