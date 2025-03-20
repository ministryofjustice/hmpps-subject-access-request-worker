create table location_detail
(
    dps_id varchar(40) not null
        constraint location_pk
            primary key,
    nomis_id bigint,
    name varchar(80) not null
);