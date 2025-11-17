INSERT INTO
    transaksjon_os(
    nav_trekk_id,
    transaksjons_id,
    transaksjon_status,
    trekk_id_ske,
    kvittering_status,
    aksjonskode,
    kreditor_id_tss,
    kreditor_trekk_id,
    kreditorsref,
    debitor_id,
    trekk_alternativ,
    trekk_type,
    kid,
    kilde,
   dokument_json,
    prioritet_fom_dato,
    gyldig_tom_dato
)
VALUES(
       'NavTrekkId01',
          'TransaksjonsId01',
       'SENDT',
          '123SkeID',
            'IKKE_MOTTATT',
          'NY',
       'TSSId' ,
       'KreditorTrekkId',
       'KreditorRef',
       'DebitorId',
          'LOPM',
          'KRED',
        'Kidnummer',
       'Kilde' ,
       'DokumentJson',
      '2018-01-01',
        '2018-01-01'


      ) ;

