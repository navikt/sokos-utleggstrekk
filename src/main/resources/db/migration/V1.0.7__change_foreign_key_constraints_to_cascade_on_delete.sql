
ALTER TABLE periode_til_os
DROP CONSTRAINT periode_til_os_transaksjon_os_id_fkey;

ALTER TABLE periode_til_os
    ADD CONSTRAINT periode_til_os_transaksjon_os_id_fkey
    FOREIGN KEY (transaksjon_os_id)
    REFERENCES transaksjon_os(id)
    ON DELETE CASCADE;

ALTER TABLE periode
DROP CONSTRAINT periode_fraskatt_id_fkey;

ALTER TABLE periode
    ADD CONSTRAINT periode_fraskatt_id_fkey
        FOREIGN KEY (fraskatt_id)
            REFERENCES fraskatt(id)
            ON DELETE CASCADE;

ALTER TABLE betalingsinformasjonfraskatt
DROP CONSTRAINT betalingsinformasjonfraskatt_fraskatt_id_fkey;

ALTER TABLE betalingsinformasjonfraskatt
    ADD CONSTRAINT betalingsinformasjonfraskatt_fraskatt_id_fkey
        FOREIGN KEY (fraskatt_id)
            REFERENCES fraskatt(id)
            ON DELETE CASCADE;

ALTER TABLE transaksjon_os ADD CONSTRAINT transaksjons_id_unique UNIQUE (transaksjons_id);

ALTER TABLE feilmelding
    ADD CONSTRAINT feilmelding_transaksjon_os_transaksjons_id_fkey
        FOREIGN KEY (transaksjons_id)
            REFERENCES transaksjon_os(transaksjons_id)
            ON DELETE CASCADE;
