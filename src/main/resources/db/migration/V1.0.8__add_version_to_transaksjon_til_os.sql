
ALTER TABLE transaksjon_os
    ADD COLUMN trekkversjon smallint NOT NULL DEFAULT 1;

CREATE INDEX idx_transaksjon_os_kvitt_trekkid_vers
    ON transaksjon_os (kvittering_status, trekk_id_ske, trekkversjon DESC);   -- For metrics
