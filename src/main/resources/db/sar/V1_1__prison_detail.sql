create table prison_detail
(
    prison_id varchar(10) not null
        constraint prison_pk
            primary key,
    prison_name varchar(100) not null
);