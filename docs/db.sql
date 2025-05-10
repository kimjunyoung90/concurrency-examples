create table product
(
    id      bigint auto_increment
        primary key,
    name    varchar(100)  not null,
    stock   int           not null,
    version int default 0 not null
);