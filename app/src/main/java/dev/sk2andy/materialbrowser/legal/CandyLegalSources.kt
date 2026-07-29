package dev.sk2andy.materialbrowser.legal

enum class ThirdPartyComponent {
    AndroidX,
    Kotlin,
    MaterialIcons,
    GoogleOpenSource,
    GoogleCodeScanner,
    EasyList,
    Uassets,
}

data class ThirdPartyNotice(
    val component: ThirdPartyComponent,
    val licenseName: String,
    val sourceUrl: String,
    val licenseUrl: String,
)

object CandyLegalSources {
    const val DEVELOPER_NAME = "André Naumann"
    const val GITHUB_PROFILE_URL = "https://github.com/sk2andy"
    const val UASSETS_REVISION = "05bc031ad40c2270223f068f052970201ca1bf14"
    const val UASSETS_SHORT_REVISION = "05bc031ad40c"
    const val UASSETS_SOURCE_URL =
        "https://github.com/uBlockOrigin/uAssets/blob/$UASSETS_REVISION/filters/filters.txt"
    const val UASSETS_LICENSE_URL =
        "https://github.com/uBlockOrigin/uAssets/blob/$UASSETS_REVISION/LICENSE"

    val thirdPartyNotices: List<ThirdPartyNotice> = listOf(
        ThirdPartyNotice(
            component = ThirdPartyComponent.AndroidX,
            licenseName = "Apache License 2.0",
            sourceUrl = "https://cs.android.com/androidx/platform/frameworks/support",
            licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
        ),
        ThirdPartyNotice(
            component = ThirdPartyComponent.Kotlin,
            licenseName = "Apache License 2.0",
            sourceUrl = "https://github.com/JetBrains/kotlin",
            licenseUrl = "https://github.com/JetBrains/kotlin/blob/v1.9.24/license/LICENSE.txt",
        ),
        ThirdPartyNotice(
            component = ThirdPartyComponent.MaterialIcons,
            licenseName = "Apache License 2.0",
            sourceUrl = "https://github.com/google/material-design-icons",
            licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
        ),
        ThirdPartyNotice(
            component = ThirdPartyComponent.GoogleOpenSource,
            licenseName = "Apache License 2.0",
            sourceUrl = "https://opensource.google/projects",
            licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
        ),
        ThirdPartyNotice(
            component = ThirdPartyComponent.GoogleCodeScanner,
            licenseName = "Google APIs Terms of Service",
            sourceUrl = "https://developers.google.com/ml-kit/vision/barcode-scanning/code-scanner",
            licenseUrl = "https://developers.google.com/terms",
        ),
        ThirdPartyNotice(
            component = ThirdPartyComponent.EasyList,
            licenseName = "CC BY-SA 3.0",
            sourceUrl = "https://github.com/easylist/easylist",
            licenseUrl = "https://creativecommons.org/licenses/by-sa/3.0/",
        ),
        ThirdPartyNotice(
            component = ThirdPartyComponent.Uassets,
            licenseName = "GPL-3.0",
            sourceUrl = UASSETS_SOURCE_URL,
            licenseUrl = UASSETS_LICENSE_URL,
        ),
    )

    val destinations: List<String>
        get() = buildList {
            add(GITHUB_PROFILE_URL)
            thirdPartyNotices.forEach { notice ->
                add(notice.sourceUrl)
                add(notice.licenseUrl)
            }
        }.distinct()
}
