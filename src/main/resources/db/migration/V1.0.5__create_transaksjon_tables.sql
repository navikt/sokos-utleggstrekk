
DROP TABLE IF EXISTS transaksjon_os;



-- TODO: Lagre hele greia? Som tekst eller felter? Egen tabell?
-- TODO: Må ha perioder også
-- TODO: Må ha kilde også
CREATE TABLE transaksjon_os
(
    id                     bigserial primary key,
    transaksjon_id         text  NOT NULL,
    fraskatt_trekk_id      text NOT NULL,
    nav_trekk_id           text NOT NULL DEFAULT '',
    transaksjon_status     text NOT NULL,
    kvittering_status      text  NOT NULL,
    aksjonskode            text NOT NULL,
    trekkalternativ        text NOT NULL,
    tidspunkt_sendt        timestamp NOT NULL DEFAULT NOW(),
    tidspunkt_siste_status timestamp NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sendt_til_os_fraskatt_id ON transaksjon_os (id, fraskatt_trekk_id);
