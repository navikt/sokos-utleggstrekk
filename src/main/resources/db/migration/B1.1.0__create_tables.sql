DROP TABLE IF EXISTS utleggstrekk;
DROP TABLE IF EXISTS trekkperiode;
DROP TABLE IF EXISTS feilkoder;

DROP TABLE IF EXISTS fraskatt;
CREATE TABLE fraskatt
(
    id                  BIGSERIAL   PRIMARY KEY,
    trekkid             TEXT        NOT NULL,
    sekvensnummer       INT         NOT NULL,
    trekkversjon        SMALLINT    NOT NULL,
    opprettet           TEXT        NOT NULL,
    saksnummer          TEXT        NOT NULL,
    trekkpliktig        TEXT        NOT NULL,
    skyldner            TEXT        NOT NULL,
    trekkstatus         TEXT        NOT NULL,
    tidspunkt_opprettet timestamptz NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_fraskatt_sekvensnummer ON fraskatt (sekvensnummer);
CREATE UNIQUE INDEX IF NOT EXISTS idxu_trekkfraskatt ON fraskatt (trekkid, trekkversjon);

DROP TABLE IF EXISTS fraskatt_status;
CREATE TABLE fraskatt_status
(
    id              BIGSERIAL   PRIMARY KEY,
    fraskatt_id     BIGSERIAL   NOT NULL REFERENCES fraskatt(id) ON DELETE CASCADE,
    status          TEXT        NOT NULL,
    tidspunkt_satt  timestamptz NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_fraskatt_status_fraskatt_id ON fraskatt_status (id, fraskatt_id);

DROP TABLE IF EXISTS periode;
CREATE TABLE periode
(
    id              BIGSERIAL   PRIMARY KEY,
    fraskatt_id     BIGSERIAL   NOT NULL REFERENCES fraskatt(id) ON DELETE CASCADE,
    trekk_id_ske    TEXT        NOT NULL,
    dato_start      TEXT        NOT NULL,
    dato_slutt      TEXT        NULL,
    trekkbelop      DECIMAL     NULL,
    trekkprosent    DECIMAL     NULL
);

DROP TABLE IF EXISTS betalingsinformasjonfraskatt;
CREATE TABLE betalingsinformasjonfraskatt
(
    id                  BIGSERIAL   PRIMARY KEY,
    fraskatt_id         BIGSERIAL   NOT NULL REFERENCES fraskatt(id) ON DELETE CASCADE,
    betalingsmottaker   TEXT        NOT NULL,
    kidnummer           TEXT        NOT NULL,
    kontonummer         TEXT        NOT NULL
);

CREATE INDEX idx_betalingsinformasjonfraskatt_fraskatt_id ON betalingsinformasjonfraskatt (id, fraskatt_id);

DROP TABLE IF EXISTS transaksjon_os;
CREATE TABLE transaksjon_os
(
    id                      BIGSERIAL   PRIMARY KEY,
    nav_trekk_id            TEXT        NOT NULL DEFAULT '',
    transaksjons_id         TEXT        UNIQUE NOT NULL,
    transaksjon_status     TEXT        NOT NULL,
    trekk_id_ske            TEXT        NOT NULL,
    kvittering_status       TEXT        NOT NULL,
    tidspunkt_sendt         timestamptz NULL DEFAULT NULL,
    tidspunkt_siste_status  timestamptz NOT NULL DEFAULT NOW(),
    aksjonskode             TEXT        NOT NULL,
    kreditor_id_tss         TEXT        NOT NULL,
    kreditor_trekk_id       TEXT        NOT NULL,
    kreditorsref            TEXT        NOT NULL,
    debitor_id              TEXT        NOT NULL,
    trekk_alternativ        TEXT        NOT NULL,
    trekk_type              TEXT        NOT NULL,
    kid                     TEXT        NOT NULL,
    kilde                   TEXT        NOT NULL,
    saldo                   DECIMAL     NOT NULL DEFAULT 0.0,
    prioritet_fom_dato      TEXT        NULL,
    gyldig_tom_dato         TEXT        NULL DEFAULT NULL,
    dokument_json           TEXT        NOT NULL,
    trekkversjon            SMALLINT    NOT NULL DEFAULT 1
);

CREATE INDEX idx_sendt_til_os_fraskatt_id ON transaksjon_os (id, trekk_id_ske);
CREATE INDEX idx_transaksjon_os_kvitt_trekkid_vers ON transaksjon_os (kvittering_status, trekk_id_ske, trekkversjon DESC);   -- For metrics

DROP TABLE IF EXISTS periode_til_os;
CREATE TABLE periode_til_os
(
    id                  BIGSERIAL   PRIMARY KEY,
    transaksjon_os_id   BIGSERIAL   NOT NULL REFERENCES transaksjon_os(id) ON DELETE CASCADE,
    sats                DECIMAL     NOT NULL,
    periode_fom_dato    TEXT        NOT NULL,
    periode_tom_dato    TEXT        NULL
);

DROP TABLE IF EXISTS feilmelding;
CREATE TABLE feilmelding
(
    id                  BIGSERIAL   PRIMARY KEY,
    kreditor_trekk_id   TEXT        NOT NULL,
    transaksjons_id     TEXT        NOT NULL REFERENCES transaksjon_os(transaksjons_id) ON DELETE CASCADE,
    trekkalternativ     TEXT        NOT NULL,
    feilkode            TEXT        NULL,
    beskrivelse         TEXT        NULL,
    tidspunkt_opprettet timestamptz NOT NULL DEFAULT NOW()
);