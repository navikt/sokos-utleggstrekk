drop table if exists fraskatt;
create table "fraskatt"
(
    id                     bigserial primary key,
    trekkid                text      NOT NULL,
    sekvensnummer          int       NOT NULL,
    trekkversjon           smallint  NOT NULL,
    opprettet              text      NOT NULL,
    saksnummer             text      NOT NULL,
    trekkpliktig           text      NOT NULL,
    skyldner               text      NOT NULL,
    trekkstatus            text      NOT NULL,
    tidspunkt_opprettet    timestamp NOT NULL DEFAULT NOW()
);


drop table if exists periode;
create table "periode"
(
    id                  bigserial primary key,
    fraskatt_id         bigserial NOT NULL references fraskatt(id),
    trekk_id_ske        text NOT NULL,
    dato_start          text      NOT NULL,
    dato_slutt          text      NULL,
    trekkbelop          decimal   NULL,
    trekkprosent        decimal      NULL
);


drop table if exists betalingsinformasjonfraskatt;
create table "betalingsinformasjonfraskatt"
(
    id                  bigserial primary key,
    fraskatt_id         bigserial      NOT NULL references fraskatt(id),
    betalingsmottaker   text      NOT NULL,
    kidnummer           text      NOT NULL,
    kontonummer         text      NOT NULL
);

drop table if exists feilmelding;
create table "feilmelding"
(
    id                  bigserial primary key,
    kreditor_trekk_id   text NOT NULL,
    transaksjons_id      text NOT NULL,
    trekkalternativ     text NOT NULL,
    feilkode            text NULL,
    beskrivelse         text NULL,
    tidspunkt_opprettet timestamp NOT NULL DEFAULT NOW()
);
create unique index if not exists idxu_trekk on utleggstrekk (trekkid_ske, sekvensnummer, trekkversjon);