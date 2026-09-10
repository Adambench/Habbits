package dev.adambench.habbits.ui

/** A transient snackbar message, optionally with an undo action. */
data class DayMessage(
    val text: String,
    val undo: (() -> Unit)? = null,
)
