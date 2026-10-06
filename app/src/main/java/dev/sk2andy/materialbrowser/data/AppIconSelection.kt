package dev.sk2andy.materialbrowser.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** The enabled launcher alias is the persisted icon choice. */
internal enum class AppIconOption(
    val aliasName: String,
) {
    Classic("LauncherIconClassic"),
    Cookie("LauncherIconCookie"),
    CookieMaxed("LauncherIconCookieMaxed"),
    CottonCandy("LauncherIconCottonCandy"),
    CottonCandyFreeform("LauncherIconCottonCandyFreeform"),
    Lemon("LauncherIconLemon"),
    LemonMaxed("LauncherIconLemonMaxed"),
    Donut("LauncherIconDonut"),
    DonutMaxed("LauncherIconDonutMaxed"),
    JellyBeans("LauncherIconJellyBeans"),
    ;

    fun componentName(context: Context): ComponentName = ComponentName(
        context.packageName,
        "dev.sk2andy.materialbrowser.$aliasName",
    )
}

internal class AppIconSelection(private val context: Context) {
    private val packageManager: PackageManager = context.packageManager

    fun selected(): AppIconOption = AppIconOption.entries.firstOrNull { option ->
        option != AppIconOption.Classic &&
            packageManager.getComponentEnabledSetting(option.componentName(context)) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    } ?: AppIconOption.Classic

    fun select(option: AppIconOption) {
        packageManager.setComponentEnabledSettings(
            AppIconOption.entries.map { entry ->
                PackageManager.ComponentEnabledSetting(
                    entry.componentName(context),
                    if (entry == option) {
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    } else {
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    },
                    PackageManager.DONT_KILL_APP,
                )
            },
        )
    }
}
