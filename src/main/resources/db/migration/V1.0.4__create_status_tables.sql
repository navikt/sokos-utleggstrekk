DROP TABLE IF EXISTS fraskatt_status;

CREATE TABLE fraskatt_status
(
    id                     bigserial primary key,
    fraskatt_id            bigserial NOT NULL,
    status                 text      NOT NULL,
    tidspunkt_satt         timestamp NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_betalingsinformasjonfraskatt_fraskatt_id ON betalingsinformasjonfraskatt (id, fraskatt_id);
CREATE INDEX idx_fraskatt_status_fraskatt_id ON fraskatt_status (id, fraskatt_id);

CREATE INDEX idx_fraskatt_sekvensnummer ON fraskatt (sekvensnummer);

