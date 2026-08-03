create sequence if not exists ai_chat_seq start with 1 increment by 50;
create sequence if not exists card_seq start with 1 increment by 50;
create sequence if not exists deck_seq start with 1 increment by 50;
create sequence if not exists tag_seq start with 1 increment by 50;
create sequence if not exists theme_seq start with 1 increment by 50;
create sequence if not exists user_card_srs_seq start with 1 increment by 50;
create sequence if not exists user_seq start with 1 increment by 50;

create table if not exists theme (
    id bigint not null,
    active boolean,
    css_url varchar(255),
    description varchar(255),
    name varchar(255) not null,
    primary key (id)
);

create table if not exists "user" (
    id bigint not null,
    is_active boolean not null,
    is_admin boolean not null,
    name varchar(255) not null,
    password_hash varchar(255) not null,
    profile_pic_url varchar(255) not null,
    theme_id bigint,
    username varchar(255) not null,
    primary key (id),
    constraint uk_user_username unique (username)
);

create table if not exists tag (
    id bigint not null,
    name varchar(255),
    primary key (id),
    constraint uk_tag_name unique (name)
);

create table if not exists deck (
    id bigint not null,
    description varchar(255),
    name varchar(255),
    template_back text,
    template_front text,
    user_id bigint not null,
    primary key (id)
);

create table if not exists card (
    id bigint not null,
    back text,
    front text,
    deck_id bigint not null,
    primary key (id)
);

create table if not exists ai_chat (
    id bigint not null,
    answer text,
    created_at bigint not null,
    question text not null,
    user_id bigint not null,
    primary key (id)
);

create table if not exists user_card_srs (
    id bigint not null,
    ease_factor real not null,
    interval_days integer not null,
    last_reviewed_at timestamp(6),
    next_review_at timestamp(6) not null,
    repetitions integer not null,
    card_id bigint not null,
    user_id bigint not null,
    primary key (id),
    constraint uk_user_card_srs_user_card unique (user_id, card_id)
);

create table if not exists card_tag (
    card_id bigint not null,
    tag_id bigint not null,
    primary key (card_id, tag_id)
);

create table if not exists deck_tag (
    deck_id bigint not null,
    tag_id bigint not null,
    primary key (deck_id, tag_id)
);

alter table if exists ai_chat
    add constraint fk_ai_chat_user foreign key (user_id) references "user";
alter table if exists card
    add constraint fk_card_deck foreign key (deck_id) references deck;
alter table if exists card_tag
    add constraint fk_card_tag_card foreign key (card_id) references card;
alter table if exists card_tag
    add constraint fk_card_tag_tag foreign key (tag_id) references tag;
alter table if exists deck
    add constraint fk_deck_user foreign key (user_id) references "user";
alter table if exists deck_tag
    add constraint fk_deck_tag_deck foreign key (deck_id) references deck;
alter table if exists deck_tag
    add constraint fk_deck_tag_tag foreign key (tag_id) references tag;
alter table if exists user_card_srs
    add constraint fk_user_card_srs_card foreign key (card_id) references card;
alter table if exists user_card_srs
    add constraint fk_user_card_srs_user foreign key (user_id) references "user";
