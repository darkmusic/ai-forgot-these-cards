alter table deck
    add column if not exists always_applied_template_front text,
    add column if not exists always_applied_template_back text;
