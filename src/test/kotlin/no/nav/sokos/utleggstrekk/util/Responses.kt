package no.nav.sokos.utleggstrekk.util
object Responses {
    //language=json
    val utleggsTrekkListeFraSkatt =
        """
[
  {
    "trekkid": "1",
    "trekkversjon": 1,
    "sekvensnummer": 1,
    "opprettet": "2024-06-16T13:33:05.672Z",
    "saksnummer": "sak-2023-899",
    "trekkpliktig": "889640782",
    "skyldner": "19628198007",
    "trekkstatus": "aktiv",
    "trekkstoerrelseForPeriode": [
      {
        "startdato": "2023-06-13",
        "sluttdato": "2024-11-30",
        "trekkbeloep": {
          "trekkbeloep": 5000.0
        }
      },
      {
        "startdato": "2024-12-01",
        "sluttdato": "2024-12-31",
        "trekkprosent": {
          "trekkprosent": 5.0
        }
      },
      {
        "startdato": "2025-01-01",
        "trekkbeloep": {
          "trekkbeloep": 2000.0
        }
      }
    ],
    "betalingsinformasjon": {
      "betalingsmottaker": "971648198",
      "kidnummer": "17654202404",
      "kontonummer": "76940512057"
    }
  },
  {
    "trekkid": "2_xx",
    "trekkversjon": 1,
    "sekvensnummer": 2,
    "opprettet": "2024-06-16T14:33:05.672Z",
    "saksnummer": "sak-2023-900",
    "trekkpliktig": "889640782",
    "skyldner": "11656296129",
    "trekkstatus": "aktiv",
    "trekkstoerrelseForPeriode": [
      {
        "startdato": "2023-06-13",
        "sluttdato": "2024-11-30",
        "trekkbeloep": {
          "trekkbeloep": 800.5
        }
      }
    ],
    "betalingsinformasjon": {
      "betalingsmottaker": "971648198",
      "kidnummer": "45645202405",
      "kontonummer": "76940512057"
    }
  }
] """.trimIndent()

    val trekkSomSkalSendes =
        """
        [
            {
                "first": {
                    "utleggstrekkTableId": 1,
                    "sekvensnummer": 1,
                    "saksnummer": "sak-2023-899",
                    "trekkidSke": "1",
                    "trekkversjon": 1,
                    "opprettetSke": "2024-06-16T15:33:05.672",
                    "trekkpliktig": "889640782",
                    "skyldner": "19628198007",
                    "trekkstatus": "aktiv",
                    "kid": "17654202404",
                    "kontonummer": "76940512057",
                    "betalingsmottaker": "971648198",
                    "corrid": "30df31bc-b7d2-405a-aeca-71507a81350b",
                    "status": "MOTTATT",
                    "tidspunktSisteStatus": "2025-04-27T00:53:00.431799",
                    "tidspunktOpprettet": "2025-04-27T00:53:00.431799"
                },
                "second": [
                    {
                        "dokument": {
                            "transaksjonsId": "30df31bc-b7d2-405a-aeca-71507a81350b",
                            "innrapporteringTrekk": {
                                "aksjonskode": "NY",
                                "kreditorIdTss": "971648198",
                                "kreditorTrekkId": "1P",
                                "debitorId": "19628198007",
                                "kodeTrekkAlternativ": "LOPP",
                                "kid": "17654202404",
                                "kreditorsRef": "sak-2023-899",
                                "saldo": 0.0,
                                "prioritetFomDato": "2024-06-16",
                                "perioder": {
                                    "periode": [
                                        {
                                            "periodeFomDato": "2024-12-01",
                                            "periodeTomDato": "2024-12-31",
                                            "sats": 5.0
                                        },
                                        {
                                            "periodeFomDato": "2023-06-13",
                                            "periodeTomDato": "2024-11-30",
                                            "sats": 0.0
                                        },
                                        {
                                            "periodeFomDato": "2025-01-01",
                                            "periodeTomDato": "9999-12-31",
                                            "sats": 0.0
                                        }
                                    ]
                                }
                            }
                        }
                    },
                    {
                        "dokument": {
                            "transaksjonsId": "30df31bc-b7d2-405a-aeca-71507a81350b",
                            "innrapporteringTrekk": {
                                "aksjonskode": "NY",
                                "kreditorIdTss": "971648198",
                                "kreditorTrekkId": "1M",
                                "debitorId": "19628198007",
                                "kodeTrekkAlternativ": "LOPM",
                                "kid": "17654202404",
                                "kreditorsRef": "sak-2023-899",
                                "saldo": 0.0,
                                "prioritetFomDato": "2024-06-16",
                                "perioder": {
                                    "periode": [
                                        {
                                            "periodeFomDato": "2023-06-13",
                                            "periodeTomDato": "2024-11-30",
                                            "sats": 5000.0
                                        },
                                        {
                                            "periodeFomDato": "2025-01-01",
                                            "periodeTomDato": "9999-12-31",
                                            "sats": 2000.0
                                        },
                                        {
                                            "periodeFomDato": "2024-12-01",
                                            "periodeTomDato": "2024-12-31",
                                            "sats": 0.0
                                        }
                                    ]
                                }
                            }
                        }
                    }
                ]
            },
            {
                "first": {
                    "utleggstrekkTableId": 2,
                    "sekvensnummer": 2,
                    "saksnummer": "sak-2023-900",
                    "trekkidSke": "2_xx",
                    "trekkversjon": 1,
                    "opprettetSke": "2024-06-16T16:33:05.672",
                    "trekkpliktig": "889640782",
                    "skyldner": "11656296129",
                    "trekkstatus": "aktiv",
                    "kid": "45645202405",
                    "kontonummer": "76940512057",
                    "betalingsmottaker": "971648198",
                    "corrid": "1ea68ffd-955b-448d-be1d-7524f31d10ae",
                    "status": "MOTTATT",
                    "tidspunktSisteStatus": "2025-04-27T00:53:00.431799",
                    "tidspunktOpprettet": "2025-04-27T00:53:00.431799"
                },
                "second": [
                    {
                        "dokument": {
                            "transaksjonsId": "1ea68ffd-955b-448d-be1d-7524f31d10ae",
                            "innrapporteringTrekk": {
                                "aksjonskode": "NY",
                                "kreditorIdTss": "971648198",
                                "kreditorTrekkId": "2_xxM",
                                "debitorId": "11656296129",
                                "kodeTrekkAlternativ": "LOPM",
                                "kid": "45645202405",
                                "kreditorsRef": "sak-2023-900",
                                "saldo": 0.0,
                                "prioritetFomDato": "2024-06-16",
                                "perioder": {
                                    "periode": [
                                        {
                                            "periodeFomDato": "2023-06-13",
                                            "periodeTomDato": "2024-11-30",
                                            "sats": 800.5
                                        }
                                    ]
                                }
                            }
                        }
                    }
                ]
            }
        ]
    """.trimIndent()
}

