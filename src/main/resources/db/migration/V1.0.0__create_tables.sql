drop table if exists trekk;
create table "trekk"
(
    id                     bigserial primary key,
    sekvensnr              numeric,
    trekkid_ske            text,
    trekkversjon           smallint,
    trekkopprettet         Timestamp,
    trekkpliktig           text,
    skyldner               text,
    trekkstatus            text,
    startPeriode           text,
    sluttPeriode           text,
    trekkbelop             decimal,
    trekkprosent           decimal,
    kid                    text,
    kontonummer            text,
    corrid                 text,
    status                 text,
    tidspunkt_mottatt      timestamp NOT NULL DEFAULT NOW(),
    tidspunkt_sendt_os     timestamp null,
    tidspunkt_siste_status timestamp NOT NULL DEFAULT NOW(),
    tidspunkt_opprettet    timestamp NOT NULL DEFAULT NOW()
);

drop table if exists generertetrekk;
create table "generertetrekk"
(
    id                     bigserial primary key,
    sekvensnr              numeric,
    sekvensnr_nav          numeric,
    trekkid_ske            text,
    trekkversjon           smallint,
    trekkopprettet         Timestamp,
    trekkpliktig           text,
    skyldner               text,
    trekkstatus            text,
    startPeriode           text,
    sluttPeriode           text,
    trekkbelop             decimal,
    trekkprosent           decimal,
    kid                    text,
    kontonummer            text,
    corrid                 text,
    status                 text,
    tidspunkt_mottatt      timestamp NOT NULL DEFAULT NOW(),
    tidspunkt_sendt_os     timestamp null,
    tidspunkt_siste_status timestamp NOT NULL DEFAULT NOW(),
    tidspunkt_opprettet    timestamp NOT NULL DEFAULT NOW()
);

drop table if exists midlertidigstans;
create table "midlertidigstans"
(
    id              bigserial primary key,
    trekksekvensnr  numeric,
    startPeriode    text,
    sluttPeriode    text
);

create index if not exists idxmidlertidigtrekksekvensnr on midlertidigstans (trekksekvensnr);
create index if not exists idxtrekksekvensnr on trekk(sekvensnr);
create index if not exists idxtrekkid_ske on trekk (trekkid_ske, trekkversjon);
create index if not exists idxsekvensnr_nav on generertetrekk (sekvensnr, sekvensnr_nav);
