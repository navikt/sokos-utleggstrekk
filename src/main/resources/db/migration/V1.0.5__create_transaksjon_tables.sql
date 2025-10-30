DROP TABLE IF EXISTS transaksjon_os;



-- TODO: Lagre hele greia? Som tekst eller felter? Egen tabell?
-- TODO: Må ha perioder også
-- TODO: Må ha kilde også
CREATE TABLE transaksjon_os
(
    id                     bigserial primary key,
    nav_trekk_id           text      NOT NULL DEFAULT '',
    transaksjon_id         text      NOT NULL,
    transaksjon_status     text      NOT NULL,
    trekk_id_ske           text      NOT NULL,
    kvittering_status      text      NOT NULL,
    tidspunkt_sendt        timestamp NOT NULL DEFAULT NOW(),
    tidspunkt_siste_status timestamp NOT NULL DEFAULT NOW(),

    aksjonskode     text NOT NULL,
    kreditor_id_tss text not null,
    kreditor_trekk_id text not null,
    kreditorsref text not null,
    debitor_id text not null,
    trekk_alternativ text NOT NULL,
    trekk_type   text not null,
    kid text not null,
    kilde text not null,
    saldo text not null,
    prioritet_fom_dato text not null,
    gyldig_tom_dato text null
);

CREATE TABLE periode_til_os
(
    id               bigserial primary key,
    transaksjon_os_id bigserial NOT NULL references transaksjon_os(id),
    sats             decimal   NOT NULL,
    fom              text      not null,
    tom              text      not null
);

CREATE INDEX idx_sendt_til_os_fraskatt_id ON transaksjon_os (id, trekk_id_ske);

ALTER TABLE trekkperiode
    ADD COLUMN status text NOT NULL DEFAULT 'IKKE_SENDT';

ALTER TABLE trekkperiode
    ADD COLUMN transaksjons_os_id bigserial NOT NULL;