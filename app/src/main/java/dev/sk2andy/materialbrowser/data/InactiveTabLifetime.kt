package dev.sk2andy.materialbrowser.data

enum class InactiveTabLifetime(
    val wireValue: String,
    val maxAgeMillis: Long?,
) {
    Never("never", null),
    Immediately("immediately", null),
    SixHours("6_hours", 21_600_000L),
    OneDay("1_day", 86_400_000L),
    ThreeDays("3_days", 259_200_000L),
    SevenDays("7_days", 604_800_000L),
    ThirtyDays("30_days", 2_592_000_000L),
    ;

    companion object {
        fun fromWireValue(value: String?): InactiveTabLifetime =
            entries.firstOrNull { it.wireValue == value } ?: Never
    }
}
