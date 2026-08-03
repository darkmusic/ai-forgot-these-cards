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

alter table tts_audio drop column preset_id;
alter table tts_audio drop column preset_name;
alter table tts_audio drop column preset_config_json;

alter table card drop column tts_text;
alter table card drop column tts_preset_id;
alter table card drop column tts_display_side;

alter table deck drop column tts_default_preset_id;

drop table if exists tts_preset;
drop table if exists tts_preset_seq;
