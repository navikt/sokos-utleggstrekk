INSERT INTO utleggstrekk (sekvensnummer, trekkid_ske, trekkid_nav, trekkversjon, saksnummer, opprettet_ske, trekkpliktig, skyldner, trekkstatus,
                          betalingsmottaker, kid, kontonummer, corr_id, status, kvitteringlopm, kvitteringlopp, tidspunkt_sendt_os, tidspunkt_siste_status)
VALUES ( 101,
        '10342396',
        '12346',
        1,
        'SAK1',
        '2025-06-16',
        999999999,
        19074639472,
         'aktiv',
        '80000427901',
        '17654202404',
        '76940512057',
        'CorrId02',
        'MOTTATT',
        NULL,
         NULL,
        now(),
        NOW() );


insert into trekkperiode(sekvensnummer, trekkid_ske, trekkversjon, dato_start, dato_slutt, sats, trekkalternativ, kilde, tidspunkt_opprettet)
values (101,10342395, 1, '2023-06-13', '2024-11-30', 5000.00, 'LOPM', 'SOKOS-UTLEGG', now() )