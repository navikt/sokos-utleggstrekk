DROP TABLE IF EXISTS transaksjon_os;

CREATE TABLE transaksjon_os
(
    id                     bigserial primary key,
    nav_trekk_id           text     NOT NULL DEFAULT '',
    transaksjons_id         text      NOT NULL,
    transaksjon_status     text      NOT NULL,
    trekk_id_ske           text      NOT NULL,
    kvittering_status      text      NOT NULL,
    tidspunkt_sendt        timestamp NULL DEFAULT NULL,
    tidspunkt_siste_status timestamp NOT NULL DEFAULT now(),

    aksjonskode     text NOT NULL,
    kreditor_id_tss  text NOT NULL,
    kreditor_trekk_id text NOT NULL,
    kreditorsref text NOT NULL,
    debitor_id text NOT NULL,
    trekk_alternativ text NOT NULL,
    trekk_type   text NOT NULL,
    kid text NOT NULL,
    kilde text NOT NULL,
    saldo decimal NOT NULL default 0.0,
    prioritet_fom_dato text NOT NULL,
    gyldig_tom_dato text NULL DEFAULT NULL,
    dokument_json text NOT NULL

);

CREATE TABLE periode_til_os
(
    id               bigserial primary key,
    transaksjon_os_id bigserial NOT NULL references transaksjon_os(id),
    sats             decimal   NOT NULL,
    periode_fom_dato    text NOT NULL,
    periode_tom_dato     text NOT NULL
);

CREATE INDEX idx_sendt_til_os_fraskatt_id ON transaksjon_os (id, trekk_id_ske);
