package dev.sk2andy.materialbrowser.browser.integration

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.service.voice.VoiceInteractionService

data class AssistantSummaryRequest(
    val url: String,
    val title: String,
    val prompt: String,
) {
    companion object {
        fun create(
            url: String,
            title: String,
            instruction: String,
        ): AssistantSummaryRequest? {
            val normalizedUrl = BrowserUriPolicy.normalizeHttpUrl(url) ?: return null
            val normalizedInstruction = instruction.trim()
            if (normalizedInstruction.isEmpty()) return null
            val normalizedTitle = title.trim()
            val prompt = buildString {
                append(normalizedInstruction)
                append("\n\n")
                if (normalizedTitle.isNotEmpty()) {
                    append(normalizedTitle)
                    append('\n')
                }
                append(normalizedUrl)
            }
            return AssistantSummaryRequest(
                url = normalizedUrl,
                title = normalizedTitle,
                prompt = prompt,
            )
        }
    }
}

sealed interface AssistantSummaryResult {
    data object Launched : AssistantSummaryResult
    data object Unsupported : AssistantSummaryResult
}

internal object AssistantTargetPolicy {
    private const val ANDROID_SYSTEM_PACKAGE = "android"
    private const val GOOGLE_APP_PACKAGE = "com.google.android.googlequicksearchbox"
    private const val GEMINI_APP_PACKAGE = "com.google.android.apps.bard"

    fun deliveryPackages(
        activeVoiceServicePackage: String?,
        resolvedAssistActivityPackage: String?,
    ): List<String> = buildList {
        when (activeVoiceServicePackage) {
            GOOGLE_APP_PACKAGE -> add(GEMINI_APP_PACKAGE)
            null -> Unit
            else -> add(activeVoiceServicePackage)
        }
        resolvedAssistActivityPackage
            ?.takeUnless {
                it == ANDROID_SYSTEM_PACKAGE ||
                    it == GOOGLE_APP_PACKAGE ||
                    it in this
            }
            ?.let(::add)
    }
}

class AssistantSummaryLauncher(private val context: Context) {
    fun launch(request: AssistantSummaryRequest): AssistantSummaryResult {
        val resolvedAssistActivityPackage = context.packageManager.resolveActivity(
            Intent(Intent.ACTION_ASSIST),
            PackageManager.MATCH_DEFAULT_ONLY,
        )?.activityInfo?.packageName
        val packages = AssistantTargetPolicy.deliveryPackages(
            activeVoiceServicePackage = activeVoiceServicePackage(),
            resolvedAssistActivityPackage = resolvedAssistActivityPackage,
        )
        return packages.firstNotNullOfOrNull { packageName ->
            launch(packageName, request)
        } ?: AssistantSummaryResult.Unsupported
    }

    private fun activeVoiceServicePackage(): String? =
        context.packageManager.queryIntentServices(
            Intent(VoiceInteractionService.SERVICE_INTERFACE),
            PackageManager.ResolveInfoFlags.of(0L),
        ).firstNotNullOfOrNull { resolvedService ->
            val service = resolvedService.serviceInfo ?: return@firstNotNullOfOrNull null
            val component = ComponentName(service.packageName, service.name)
            service.packageName.takeIf {
                runCatching {
                    VoiceInteractionService.isActiveService(context, component)
                }.getOrDefault(false)
            }
        }

    private fun launch(
        packageName: String,
        request: AssistantSummaryRequest,
    ): AssistantSummaryResult? {
        val target = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .setPackage(packageName)
            .putExtra(Intent.EXTRA_TEXT, request.prompt)
            .putExtra(Intent.EXTRA_TITLE, request.title)
            .putExtra(Intent.EXTRA_SUBJECT, request.title)
            .apply {
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        return try {
            context.startActivity(target)
            AssistantSummaryResult.Launched
        } catch (_: ActivityNotFoundException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }
}
