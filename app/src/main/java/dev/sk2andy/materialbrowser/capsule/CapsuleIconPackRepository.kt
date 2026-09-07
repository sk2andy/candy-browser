package dev.sk2andy.materialbrowser.capsule

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import android.util.Xml
import java.io.ByteArrayInputStream

@SuppressLint("DiscouragedApi", "UseCompatLoadingForDrawables")
class CapsuleIconPackRepository(context: Context) {
    private val applicationContext = context.applicationContext
    private val packageManager = applicationContext.packageManager
    private val bitmapCache = object : LruCache<String, Bitmap>(MAX_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    fun discover(): List<CapsuleIconPack> = ICON_PACK_ACTIONS
        .asSequence()
        .flatMap { intentSpec ->
            runCatching {
                packageManager.queryIntentActivities(
                    Intent(intentSpec.action).apply {
                        intentSpec.category?.let(::addCategory)
                    },
                    PackageManager.ResolveInfoFlags.of(0),
                )
            }.getOrDefault(emptyList()).asSequence()
        }
        .mapNotNull { resolveInfo -> resolveInfo.activityInfo?.packageName }
        .distinct()
        .mapNotNull(::loadPack)
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, CapsuleIconPack::label))
        .toList()

    fun loadEntries(pack: CapsuleIconPack): List<CapsuleIconPackEntry> = runCatching {
        val resources = packageManager.getResourcesForApplication(pack.packageName)
        val entries = CATALOG_NAMES.flatMap { catalogName ->
            buildList {
                runCatching {
                    loadResourceCatalog(resources, pack.packageName, catalogName)
                }.getOrNull()?.let(::addAll)
                runCatching {
                    loadAssetCatalog(resources, pack.packageName, catalogName)
                }.getOrNull()?.let(::addAll)
            }
        }
        CapsuleIconPackRules.mergeEntries(entries)
    }.getOrNull().orEmpty()

    fun render(entry: CapsuleIconPackEntry): Bitmap? {
        val key = "${entry.packageName}:${entry.drawableName}"
        synchronized(bitmapCache) { bitmapCache.get(key) }?.let { return it }
        val bitmap = renderUncached(entry) ?: return null
        synchronized(bitmapCache) { bitmapCache.put(key, bitmap) }
        return bitmap
    }

    private fun loadPack(packageName: String): CapsuleIconPack? = runCatching {
        val resources = packageManager.getResourcesForApplication(packageName)
        if (!hasCatalog(resources, packageName)) return null
        val applicationInfo = packageManager.getApplicationInfo(
            packageName,
            PackageManager.ApplicationInfoFlags.of(0),
        )
        CapsuleIconPack(
            packageName = packageName,
            label = packageManager.getApplicationLabel(applicationInfo)
                .toString()
                .take(MAX_PACK_LABEL_LENGTH)
                .ifBlank { packageName },
        )
    }.getOrNull()

    private fun hasCatalog(resources: Resources, packageName: String): Boolean {
        return CATALOG_NAMES.any { catalogName ->
            CATALOG_RESOURCE_TYPES.any { type ->
                resources.getIdentifier(catalogName, type, packageName) != 0
            } || runCatching {
                resources.assets.open("$catalogName.xml").use { }
                true
            }.getOrDefault(false)
        }
    }

    private fun loadResourceCatalog(
        resources: Resources,
        packageName: String,
        catalogName: String,
    ): List<CapsuleIconPackEntry> {
        val entries = mutableListOf<CapsuleIconPackEntry>()
        val xmlResourceId = resources.getIdentifier(catalogName, XML_TYPE, packageName)
        if (xmlResourceId != 0) {
            runCatching {
                resources.getXml(xmlResourceId).use { parser ->
                    entries += CapsuleIconPackParser.parse(packageName, parser)
                }
            }
        }
        val rawResourceId = resources.getIdentifier(catalogName, RAW_TYPE, packageName)
        if (rawResourceId != 0) {
            runCatching {
                resources.openRawResource(rawResourceId).use { input ->
                    parseStreamCatalog(
                        packageName,
                        input.readNBytes(MAX_CATALOG_BYTES + 1),
                    )?.let(entries::addAll)
                }
            }
        }
        return entries
    }

    private fun loadAssetCatalog(
        resources: Resources,
        packageName: String,
        catalogName: String,
    ): List<CapsuleIconPackEntry>? {
        val encoded = runCatching {
            resources.assets.open("$catalogName.xml").use { input ->
                input.readNBytes(MAX_CATALOG_BYTES + 1)
            }
        }.getOrNull() ?: return null
        return parseStreamCatalog(packageName, encoded)
    }

    private fun parseStreamCatalog(
        packageName: String,
        encoded: ByteArray,
    ): List<CapsuleIconPackEntry>? {
        if (encoded.size > MAX_CATALOG_BYTES) return null
        return ByteArrayInputStream(encoded).use { input ->
            val parser = Xml.newPullParser().apply {
                setInput(input, Charsets.UTF_8.name())
            }
            CapsuleIconPackParser.parse(packageName, parser)
        }
    }

    private fun renderUncached(entry: CapsuleIconPackEntry): Bitmap? {
        return try {
            val packageContext = applicationContext.createPackageContext(
                entry.packageName,
                Context.CONTEXT_IGNORE_SECURITY,
            )
            val resources = packageContext.resources
            val resourceId = resources.getIdentifier(
                entry.drawableName,
                DRAWABLE_TYPE,
                entry.packageName,
            ).takeIf { it != 0 } ?: resources.getIdentifier(
                entry.drawableName,
                MIPMAP_TYPE,
                entry.packageName,
            )
            if (resourceId == 0) return null
            val drawable = packageContext.getDrawable(resourceId)?.mutate() ?: return null
            Bitmap.createBitmap(
                CapsuleCustomIconProcessor.OUTPUT_SIZE,
                CapsuleCustomIconProcessor.OUTPUT_SIZE,
                Bitmap.Config.ARGB_8888,
            ).also { bitmap ->
                val intrinsicWidth = drawable.intrinsicWidth
                val intrinsicHeight = drawable.intrinsicHeight
                if (intrinsicWidth > 0 && intrinsicHeight > 0) {
                    val scale = minOf(
                        bitmap.width.toFloat() / intrinsicWidth,
                        bitmap.height.toFloat() / intrinsicHeight,
                    )
                    val width = (intrinsicWidth * scale).toInt().coerceAtLeast(1)
                    val height = (intrinsicHeight * scale).toInt().coerceAtLeast(1)
                    val left = (bitmap.width - width) / 2
                    val top = (bitmap.height - height) / 2
                    drawable.setBounds(left, top, left + width, top + height)
                } else {
                    drawable.setBounds(0, 0, bitmap.width, bitmap.height)
                }
                drawable.draw(Canvas(bitmap))
            }
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    private companion object {
        const val DRAWABLE_TYPE = "drawable"
        const val MIPMAP_TYPE = "mipmap"
        const val MAX_PACK_LABEL_LENGTH = 80
        const val MAX_CACHE_BYTES = 12 * 1_024 * 1_024
        const val MAX_CATALOG_BYTES = 8 * 1_024 * 1_024
        const val XML_TYPE = "xml"
        const val RAW_TYPE = "raw"

        val CATALOG_NAMES = listOf("drawable", "appfilter")
        val CATALOG_RESOURCE_TYPES = listOf(XML_TYPE, RAW_TYPE)

        val ICON_PACK_ACTIONS = listOf(
            IconPackIntentSpec(
                action = Intent.ACTION_MAIN,
                category = "com.anddoes.launcher.THEME",
            ),
            IconPackIntentSpec(
                action = Intent.ACTION_MAIN,
                category = "com.fede.launcher.THEME_ICONPACK",
            ),
            IconPackIntentSpec(
                action = Intent.ACTION_MAIN,
                category = "com.teslacoilsw.launcher.THEME",
            ),
            IconPackIntentSpec("com.dlto.atom.launcher.THEME"),
            IconPackIntentSpec("com.gau.go.launcherex.theme"),
            IconPackIntentSpec("com.novalauncher.THEME"),
            IconPackIntentSpec("org.adw.ActivityStarter.THEMES"),
            IconPackIntentSpec("org.adw.launcher.THEMES"),
        )
    }
}

private data class IconPackIntentSpec(
    val action: String,
    val category: String? = null,
)
