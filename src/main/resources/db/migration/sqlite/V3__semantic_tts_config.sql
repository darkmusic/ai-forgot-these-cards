alter table deck add column tts_config_json varchar(32600);

alter table card add column tts_config_json varchar(32600);

alter table tts_audio add column target varchar(255);
alter table tts_audio add column variant varchar(255);
alter table tts_audio add column language varchar(255);
alter table tts_audio add column text_source varchar(255);
alter table tts_audio add column resolved_text varchar(32600);
alter table tts_audio add column voice varchar(255);
alter table tts_audio add column speed double;
alter table tts_audio add column config_json varchar(32600);

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
