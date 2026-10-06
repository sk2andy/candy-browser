package dev.sk2andy.materialbrowser.ui

import android.view.View
import android.widget.ImageView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.data.AppIconOption
import dev.sk2andy.materialbrowser.data.AppIconSelection
import dev.sk2andy.materialbrowser.shared.ui.settings.SettingsPageSpacer

internal object AppIconSettingsTestTags {
    const val Choice = "appearance_settings_app_icon"
}

@Composable
internal fun AppIconSettings() {
    val context = LocalContext.current
    val selection = remember(context) { AppIconSelection(context) }
    var selected by remember(selection) { mutableStateOf(selection.selected()) }
    var expanded by remember { mutableStateOf(false) }

    Box {
        SettingsChoice(
            title = stringResource(R.string.settings_app_icon),
            value = selected.label(),
            expanded = expanded,
            onClick = { expanded = true },
            modifier = Modifier.testTag(AppIconSettingsTestTags.Choice),
        )
        SettingsDropdown(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            AppIconOption.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AndroidView(
                                factory = { previewContext ->
                                    ImageView(previewContext).apply {
                                        setImageResource(option.iconRes)
                                        scaleType = ImageView.ScaleType.FIT_CENTER
                                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                                    }
                                },
                                modifier = Modifier.size(40.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(option.label())
                        }
                    },
                    onClick = {
                        selection.select(option)
                        selected = option
                        expanded = false
                    },
                    modifier = Modifier.semantics { this.selected = option == selected },
                    trailingIcon = {
                        if (option == selected) {
                            Icon(Icons.Default.Check, contentDescription = null)
                        }
                    },
                )
            }
        }
    }
    SettingsPageSpacer()
}

@Composable
private fun AppIconOption.label(): String {
    val name = stringResource(labelRes)
    val variantRes = when (this) {
        AppIconOption.CookieMaxed,
        AppIconOption.LemonMaxed,
        AppIconOption.DonutMaxed -> R.string.app_icon_variant_maxed
        AppIconOption.CottonCandyFreeform -> R.string.app_icon_variant_freeform
        else -> null
    }
    return if (variantRes == null) name else "$name · ${stringResource(variantRes)}"
}

private val AppIconOption.labelRes: Int
    get() = when (this) {
        AppIconOption.Classic -> R.string.app_icon_classic
        AppIconOption.Cookie, AppIconOption.CookieMaxed -> R.string.app_icon_cookie
        AppIconOption.CottonCandy, AppIconOption.CottonCandyFreeform -> R.string.app_icon_cotton_candy
        AppIconOption.Lemon, AppIconOption.LemonMaxed -> R.string.app_icon_lemon
        AppIconOption.Donut, AppIconOption.DonutMaxed -> R.string.app_icon_donut
        AppIconOption.JellyBeans -> R.string.app_icon_jelly_beans
    }

private val AppIconOption.iconRes: Int
    get() = when (this) {
        AppIconOption.Classic -> R.mipmap.ic_launcher
        AppIconOption.Cookie -> R.mipmap.ic_launcher_cookie
        AppIconOption.CookieMaxed -> R.mipmap.ic_launcher_cookie_maxed
        AppIconOption.CottonCandy -> R.mipmap.ic_launcher_cotton_candy
        AppIconOption.CottonCandyFreeform -> R.mipmap.ic_launcher_cotton_candy_freeform
        AppIconOption.Lemon -> R.mipmap.ic_launcher_lemon
        AppIconOption.LemonMaxed -> R.mipmap.ic_launcher_lemon_maxed
        AppIconOption.Donut -> R.mipmap.ic_launcher_donut
        AppIconOption.DonutMaxed -> R.mipmap.ic_launcher_donut_maxed
        AppIconOption.JellyBeans -> R.mipmap.ic_launcher_jelly_beans
    }
