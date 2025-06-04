drop table if exists utleggstrekk;
create table "utleggstrekk"
(
    id                     bigserial primary key,
    sekvensnummer          int  NOT NULL,
    trekkid_ske            text      NOT NULL,
    trekkid_nav            text,
    trekkversjon           smallint  NOT NULL,
    saksnummer             text      NOT NULL,
    opprettet_ske          timestamp NOT NULL,
    trekkpliktig           text      NOT NULL,
    skyldner               text      NOT NULL,
    trekkstatus            text      NOT NULL,
    betalingsmottaker      text      NOT NULL,
    kid                    text      NOT NULL,
    kontonummer            text      NOT NULL,
    corrid                 text      NOT NULL,
    status                 text,
    kvittering             text,
    tidspunkt_sendt_os     timestamp ,
    tidspunkt_siste_status timestamp NOT NULL DEFAULT NOW(),
    tidspunkt_opprettet    timestamp NOT NULL DEFAULT NOW()
);

drop table if exists trekkperiode;
create table "trekkperiode"
(
    id                  bigserial primary key,
    sekvensnummer       int  NOT NULL,
    trekkid_ske         text NOT NULL,
    trekkversjon        smallint  NOT NULL,
    dato_start          text      NOT NULL,
    dato_slutt          text,
    sats                decimal NOT NULL ,
    trekkalternativ     text NOT NULL ,
    kilde               text NOT NULL,
    tidspunkt_opprettet timestamp NOT NULL DEFAULT NOW()
);

drop table if exists feilkoder;
create table "feilkoder"
(
    id                  bigserial primary key,
    Trekkid_nav         text NOT NULL,
    corr_id             text NOT NULL,
    trekkalternativ     text NOT NULL,
    feilkode            text NOT NULL,
    beskrivelse         text NOT NULL,
    tidspunkt_opprettet timestamp NOT NULL DEFAULT NOW()
);


create unique index if not exists idxu_trekk on utleggstrekk (trekkid_ske, sekvensnummer, trekkversjon);
create index if not exists idx_periode on trekkperiode (trekkid_ske, sekvensnummer, trekkversjon)

