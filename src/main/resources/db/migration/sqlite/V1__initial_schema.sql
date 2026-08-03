create table if not exists ai_chat_seq (
    next_val bigint
);
insert into ai_chat_seq (next_val)
select 1 where not exists (select 1 from ai_chat_seq);

create table if not exists card_seq (
    next_val bigint
);
insert into card_seq (next_val)
select 1 where not exists (select 1 from card_seq);

create table if not exists deck_seq (
    next_val bigint
);
insert into deck_seq (next_val)
select 1 where not exists (select 1 from deck_seq);

create table if not exists tag_seq (
    next_val bigint
);
insert into tag_seq (next_val)
select 1 where not exists (select 1 from tag_seq);

create table if not exists theme_seq (
    next_val bigint
);
insert into theme_seq (next_val)
select 1 where not exists (select 1 from theme_seq);

create table if not exists user_card_srs_seq (
    next_val bigint
);
insert into user_card_srs_seq (next_val)
select 1 where not exists (select 1 from user_card_srs_seq);

create table if not exists user_seq (
    next_val bigint
);
insert into user_seq (next_val)
select 1 where not exists (select 1 from user_seq);

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
    template_back varchar(32600),
    template_front varchar(32600),
    user_id bigint not null,
    primary key (id)
);

create table if not exists card (
    id bigint not null,
    back varchar(32600),
    front varchar(32600),
    deck_id bigint not null,
    primary key (id)
);

create table if not exists ai_chat (
    id bigint not null,
    answer varchar(32600),
    created_at bigint not null,
    question varchar(32600) not null,
    user_id bigint not null,
    primary key (id)
);

create table if not exists user_card_srs (
    id bigint not null,
    ease_factor float not null,
    interval_days integer not null,
    last_reviewed_at timestamp,
    next_review_at timestamp not null,
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
