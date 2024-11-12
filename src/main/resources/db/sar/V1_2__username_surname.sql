create table user_detail
(
    username varchar(240) not null
        constraint username_pk
            primary key,
    last_name varchar(50) not null
);