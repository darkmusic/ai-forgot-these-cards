alter table if exists deck
    add column if not exists presentation_config_json text;
