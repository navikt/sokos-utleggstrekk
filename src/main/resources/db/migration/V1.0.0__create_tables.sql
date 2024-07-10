drop table if exists krav;
create table "utleggstrekk"
(
    id                     bigserial primary key,
    trekkid_ske            text,
    trekkversjon_ske       smallint,
    trekkopprettet         Timestamp,
    trekkpliktig           text,
    skyldner               text,
    trekkstatus            text,
    startPeriode           text,
    sluttPeriode           text,
    trekkbeloep            decimal,
    trekkprosent           decimal,
    kidnummer              text,
    kontonummer            text,
    corr_id                text,
    tidspunkt_mottatt      timestamp,
    tidspunkt_sendt_os     timestamp null,
    tidspunkt_siste_status timestamp NOT NULL DEFAULT NOW(),
    tidspunkt_opprettet    timestamp NOT NULL DEFAULT NOW()
);

drop table if exists midlertidigstans;
create table "midlertidigstans"
(
    id             bigserial primary key,
    utleggstrekkid bigint,
    startPeriode   text,
    sluttPeriode   text
);

create index if not exists idxutleggstrekkid_nav on midlertidigstans (utleggstrekkid);
create index if not exists idxtrekkid_ske on utleggstrekk (trekkid_ske);
