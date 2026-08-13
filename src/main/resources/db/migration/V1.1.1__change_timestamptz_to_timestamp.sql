
ALTER TABLE transaksjon_os
    ALTER COLUMN tidspunkt_sendt TYPE timestamp,
    ALTER COLUMN tidspunkt_siste_status TYPE timestamp;

ALTER TABLE feilmelding
    ALTER COLUMN tidspunkt_opprettet TYPE timestamp;

ALTER TABLE fraskatt_status
    ALTER COLUMN tidspunkt_satt TYPE timestamp;