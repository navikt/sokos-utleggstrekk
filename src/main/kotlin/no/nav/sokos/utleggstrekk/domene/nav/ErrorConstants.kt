package no.nav.sokos.utleggstrekk.domene.nav

enum class ErrorCategory(val value: String) {
    TREKK_HENTING("Feil i henting av trekk"),
    TSS_ID("Feil i løsning av TSSId"),
    KVITTERING_UTEBLIR("Kvittering fra oppdrag uteblir"),
    KVITTERING_FEIL("Kvittering fra oppdrag feil"),
    ;

    override fun toString() = this.value
}

enum class ErrorHeader(val value: String) {
    FEIL_FRA_SKE("Feil fra SKE"),
    FEIL_VED_SENDING("Feil ved sending"),
    FEIL_I_VALIDERING("Feil i validering"),
    DATABASE_ERROR("Feil i oppdatering av transaksjons status"),

    TSSID_FEIL("TSSId finnes ikke"),

    MANGLENDE_KVITTERING("Transaksjon mangler kvitteringen"),
    KVITTERING_FEIL("Kvittering feil"),
    PROCESSING_FEIL("Prosessering av kvitteringmelding feilet."),
    ;

    override fun toString() = this.value
}
