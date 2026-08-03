update deck
set tts_config_json = '{
  "provider": "indic-parler-tts",
  "targets": {
    "word": { "enabled": true, "displaySide": "FRONT", "voices": ["hindi", "urdu"] },
    "example": { "enabled": true, "displaySide": "BACK", "voices": ["hindi", "urdu"] }
  },
  "variants": {
    "hindi": {
      "language": "hi",
      "textSource": "devanagari",
      "caption": "",
      "speaker": "",
      "generationConfig": {}
    },
    "urdu": {
      "language": "ur",
      "textSource": "urduScript",
      "caption": "",
      "speaker": "",
      "generationConfig": {}
    }
  },
  "defaultVariant": "hindi",
  "showVariantControls": "both"
}'
where tts_config_json is null or tts_config_json like '%"textSource": "ttsText"%';

alter table if exists tts_audio drop constraint if exists fk_tts_audio_preset;
alter table if exists tts_preset drop constraint if exists fk_tts_preset_deck;

alter table if exists tts_audio drop column if exists preset_id;
alter table if exists tts_audio drop column if exists preset_name;
alter table if exists tts_audio drop column if exists preset_config_json;

alter table if exists card drop column if exists tts_text;
alter table if exists card drop column if exists tts_preset_id;
alter table if exists card drop column if exists tts_display_side;

alter table if exists deck drop column if exists tts_default_preset_id;

drop table if exists tts_preset;
drop sequence if exists tts_preset_seq;
