
DROP TABLE IF EXISTS transaksjon_os;




CREATE TABLE transaksjon_os
(
    id                     bigserial primary key,
    transaksjon_id        text  NOT NULL,
    fraskatt_id            bigserial NOT NULL,
    transaksjon_status     text NOT NULL,
    kvittering_status      text  NOT NULL,
    aksjonskode            text NOT NULL,
    trekkalternativ         text NOT NULL,
    tidspunkt_sendt        timestamp NOT NULL DEFAULT NOW(),
    tidspunkt_siste_status timestamp NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sendt_til_os_fraskatt_id ON transaksjon_os (id, fraskatt_id);
