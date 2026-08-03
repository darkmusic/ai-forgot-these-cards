alter table deck
    add column if not exists tts_config_json text;

alter table card
    add column if not exists tts_config_json text;

alter table tts_audio
    add column if not exists target varchar(255);
alter table tts_audio
    add column if not exists variant varchar(255);
alter table tts_audio
    add column if not exists language varchar(255);
alter table tts_audio
    add column if not exists text_source varchar(255);
alter table tts_audio
    add column if not exists resolved_text text;
alter table tts_audio
    add column if not exists voice varchar(255);
alter table tts_audio
    add column if not exists speed double precision;
alter table tts_audio
    add column if not exists config_json text;

update deck
set tts_config_json = '{
  "provider": "indic-parler-tts",
  "targets": {
    "word": { "enabled": true, "displaySide": "BACK" }
  },
  "variants": {
    "default": {
      "language": "",
      "textSource": "ttsText",
      "caption": "",
      "speaker": "",
      "generationConfig": {}
    }
  },
  "defaultVariant": "default",
  "showVariantControls": "both"
}'
where tts_config_json is null and (tts_enabled = true or tts_model_id is not null or tts_default_preset_id is not null);

update card
set tts_config_json = '{
  "targets": {
    "word": { "enabled": true, "displaySide": "' || coalesce(tts_display_side, 'BACK') || '" }
  },
  "defaultVariant": "default"
}'
where tts_config_json is null and (tts_text is not null or tts_display_side is not null or tts_preset_id is not null);

update tts_audio
set target = coalesce(target, 'word'),
    variant = coalesce(variant, 'default'),
    text_source = coalesce(text_source, 'ttsText'),
    resolved_text = coalesce(resolved_text, null),
    config_json = coalesce(config_json, preset_config_json)
where target is null or variant is null or text_source is null or config_json is null;
