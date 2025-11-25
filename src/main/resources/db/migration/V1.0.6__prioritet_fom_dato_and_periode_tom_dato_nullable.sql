
ALTER TABLE transaksjon_os
    ALTER COLUMN prioritet_fom_dato DROP NOT NULL;

ALTER TABLE periode_til_os
    ALTER COLUMN periode_tom_dato DROP NOT NULL;
