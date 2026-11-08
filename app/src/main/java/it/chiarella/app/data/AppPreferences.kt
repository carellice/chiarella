package it.chiarella.app.data

/** Stile grafico dell'interfaccia. */
enum class UiStyle { CLASSIC, SKEUO, METRO }

data class AppPreferences(
    val showVolumeNotice: Boolean = true,
    val reducedAnimations: Boolean = false,
    val style: UiStyle = UiStyle.CLASSIC,
)
