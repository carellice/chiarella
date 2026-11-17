package it.chiarella.app.data

/** Stile grafico dell'interfaccia. */
enum class UiStyle { CLASSIC, SKEUO, METRO, NOTHING, DUEFFE }

data class AppPreferences(
    val showVolumeNotice: Boolean = true,
    val reducedAnimations: Boolean = false,
    val style: UiStyle = UiStyle.CLASSIC,
    /** "Pausa di un minuto": promemoria negli orari scelti. */
    val pauseReminders: Boolean = false,
    /** Orari delle pause, in minuti dalla mezzanotte. */
    val pauseTimes: List<Int> = listOf(11 * 60, 16 * 60),
)
