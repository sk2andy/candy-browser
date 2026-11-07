package dev.sk2andy.materialbrowser.data

enum class InactiveTabLifetime(
    val wireValue: String,
    val maxAgeMillis: Long?,
) {
    Never("never", null),
    Immediately("immediately", null),
    WhenAppCloses("when_app_closes", null),
    ThirtyMinutes("30_minutes", 1_800_000L),
    OneHour("1_hour", 3_600_000L),
    TwoHours("2_hours", 7_200_000L),
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
