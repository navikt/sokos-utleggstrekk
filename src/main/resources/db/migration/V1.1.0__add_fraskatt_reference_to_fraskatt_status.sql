
ALTER TABLE fraskatt_status
    ADD CONSTRAINT fraskatt_status_frakastt_id_fkey
        FOREIGN KEY (fraskatt_id)
            REFERENCES fraskatt(id)
            ON DELETE CASCADE;