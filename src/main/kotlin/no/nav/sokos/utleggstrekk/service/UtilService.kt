package no.nav.sokos.utleggstrekk.service

internal fun String.previousPeriodWithEndDay():String {
    val sp = this.split("-")
    val ar = sp.get(0)
    val mnd = sp.get(1)
    return when (mnd.toInt()) {
        1 -> "${ar.toInt()-1}-12-31"
        in 2..10 -> "$ar-0${mnd.toInt()-1}".addPeriodEndDay()
        else -> "$ar-${(mnd.toInt()-1)}".addPeriodEndDay()
    }
}

fun String.addPeriodEndDay(): String {
    return when (this.split("-").get(1).toInt()) {
        1,3,5,7,8,10,12 -> "$this-31"
        2 -> "$this-28"
        else -> "$this-30"
    }
}

fun String.nextPeriodWithStartDay(): String {
    val sp = this.split("-")
    val ar = sp.get(0)
    val mnd = sp.get(1)
    return when (mnd.toInt()) {
        12 -> "${ar.toInt()+1}-01-01"
        in 1..8 -> "$ar-0${mnd.toInt()+1}-01"
        else -> "$ar-${(mnd.toInt()+1)}-01"
    }
}