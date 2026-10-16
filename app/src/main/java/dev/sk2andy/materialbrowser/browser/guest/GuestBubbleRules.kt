package dev.sk2andy.materialbrowser.browser.guest

import java.util.UUID

internal enum class GuestBubblePhase {
    Active,
    Closing,
}

internal data class GuestBubbleSession(
    val id: String,
    val phase: GuestBubblePhase,
    val guestProcessId: Int? = null,
    val guestProcessStartTicks: Long? = null,
)

internal object GuestBubbleRules {
    const val WEBVIEW_DATA_SUFFIX = "guest_bubble"
    const val SESSION_DIRECTORY_NAME = "guest_bubble"
    const val GUEST_PROCESS_SUFFIX = ":guestBubble"
    const val CLEANUP_PROCESS_SUFFIX = ":guestCleanup"
    const val MAX_MARKER_BYTES = 256

    /** An unreadable marker must retain the owner lock until authenticated recovery. */
    val recoverySession = GuestBubbleSession(
        id = "00000000-0000-0000-0000-000000000000",
        phase = GuestBubblePhase.Active,
    )

    fun decodeMarker(value: String): GuestBubbleSession? {
        if (value.length > MAX_MARKER_BYTES) return null
        val fields = value.split('\n')
        if (fields.size != 5 || fields[0] != "1") return null
        val id = runCatching { UUID.fromString(fields[1]).toString() }.getOrNull()
            ?.takeIf { it == fields[1] } ?: return null
        val phase = when (fields[2]) {
            "active" -> GuestBubblePhase.Active
            "closing" -> GuestBubblePhase.Closing
            else -> return null
        }
        val processId = fields[3].takeUnless { it == "-" }?.toIntOrNull()
        val startTicks = fields[4].takeUnless { it == "-" }?.toLongOrNull()
        if (fields[3] != "-" && (processId == null || processId <= 0)) return null
        if (fields[4] != "-" && (startTicks == null || startTicks <= 0L)) return null
        if ((processId == null) != (startTicks == null)) return null
        return GuestBubbleSession(id, phase, processId, startTicks)
    }

    fun encodeMarker(session: GuestBubbleSession): String = listOf(
        "1",
        session.id,
        when (session.phase) {
            GuestBubblePhase.Active -> "active"
            GuestBubblePhase.Closing -> "closing"
        },
        session.guestProcessId?.toString() ?: "-",
        session.guestProcessStartTicks?.toString() ?: "-",
    ).joinToString("\n")

    fun isGuestProcess(packageName: String, processName: String?): Boolean =
        processName == packageName + GUEST_PROCESS_SUFFIX
}
