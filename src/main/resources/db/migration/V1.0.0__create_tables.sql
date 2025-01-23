drop table if exists trekkpaleg;
create table "trekkpalegg"
(
    id                     bigserial primary key,
    sekvensnummer          int  NOT NULL,
    trekkid_ske            text      NOT NULL,
    trekkid_nav            text,
    trekkversjon           smallint  NOT NULL,
    saksnummer             text      NOT NULL,
    opprettet_ske          text      NOT NULL,
    trekkpliktig           text      NOT NULL,
    skyldner               text      NOT NULL,
    trekkstatus            text      NOT NULL,
    betalingsmottaker      text      NOT NULL,
    kid                    text      NOT NULL,
    kontonummer            text      NOT NULL,
    corrid                 text      NOT NULL,
    status                 text      NOT NULL,
    tidspunkt_sendt_os     timestamp null,
    tidspunkt_siste_status timestamp NOT NULL DEFAULT NOW(),
    tidspunkt_opprettet    timestamp NOT NULL DEFAULT NOW()
);

drop table if exists trekkperiode;
create table "trekkperiode"
(
    id                  bigserial primary key,
    sekvensnummer       int  NOT NULL,
    trekkid_ske         bigserial NOT NULL,
    trekkversjon        smallint  NOT NULL,
    dato_start          text      NOT NULL,
    dato_slutt          text,
    trekkbelop          decimal,
    trekkprosent        decimal,
    tidspunkt_opprettet timestamp NOT NULL DEFAULT NOW()
);


create unique index if not exists idxu_trekk on trekkpalegg (trekkid_ske, sekvensnummer, trekkversjon);
create index if not exists idx_periode on trekkperiode (trekkid_ske, sekvensnummer, trekkversjon)
