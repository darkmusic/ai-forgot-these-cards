create sequence if not exists tts_audio_seq start with 1 increment by 50;
create sequence if not exists tts_preset_seq start with 1 increment by 50;

alter table deck
    add column if not exists tts_enabled boolean not null default false;
alter table deck
    add column if not exists tts_model_id varchar(255);
alter table deck
    add column if not exists tts_default_preset_id bigint;

alter table card
    add column if not exists tts_text text;
alter table card
    add column if not exists tts_preset_id bigint;
alter table card
    add column if not exists tts_display_side varchar(255);

create table if not exists tts_preset (
    id bigint not null,
    advanced_config_json text,
    caption text,
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
    file_path text not null,
    generated_at bigint not null,
    model_id varchar(255) not null,
    preset_config_json text,
    preset_name varchar(255),
    card_id bigint not null,
    deck_id bigint not null,
    preset_id bigint,
    primary key (id),
    constraint uk_tts_audio_card_cache unique (card_id, cache_key)
);

alter table if exists tts_audio
    add constraint fk_tts_audio_card foreign key (card_id) references card;
alter table if exists tts_audio
    add constraint fk_tts_audio_deck foreign key (deck_id) references deck;
alter table if exists tts_audio
    add constraint fk_tts_audio_preset foreign key (preset_id) references tts_preset;
alter table if exists tts_preset
    add constraint fk_tts_preset_deck foreign key (deck_id) references deck;
