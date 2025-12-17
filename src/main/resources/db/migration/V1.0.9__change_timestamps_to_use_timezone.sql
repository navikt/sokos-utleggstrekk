
ALTER TABLE transaksjon_os
    ALTER COLUMN tidspunkt_sendt TYPE timestamptz,
    ALTER COLUMN tidspunkt_siste_status TYPE timestamptz;

ALTER TABLE feilkoder
    ALTER COLUMN tidspunkt_opprettet TYPE timestamptz;

ALTER TABLE feilmelding
    ALTER COLUMN tidspunkt_opprettet TYPE timestamptz;

ALTER TABLE flyway_schema_history
    ALTER COLUMN installed_on TYPE timestamptz;

ALTER TABLE fraskatt
    ALTER COLUMN tidspunkt_opprettet TYPE timestamptz;

ALTER TABLE fraskatt_status
    ALTER COLUMN tidspunkt_satt TYPE timestamptz;

ALTER TABLE trekkperiode
    ALTER COLUMN tidspunkt_opprettet TYPE timestamptz;