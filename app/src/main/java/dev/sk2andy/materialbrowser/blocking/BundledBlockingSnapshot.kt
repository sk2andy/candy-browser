package dev.sk2andy.materialbrowser.blocking

import android.content.Context
import java.util.concurrent.CompletableFuture

internal data class BundledBlockingSnapshot(
    val requestBlocker: RequestBlocker,
    val advancedRules: AdvancedFilterRules,
    val proceduralRules: ProceduralCosmeticRules,
)

internal class BundledBlockingSnapshotProvider private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val requestBlockerFuture = CompletableFuture.supplyAsync {
        runCatching {
            RequestBlocker(
                hostRules = loadLinesOrEmpty(
                    "blocked_hosts.txt",
                    "uassets_blocked_hosts.txt",
                ),
                indexedHostRules = runCatching {
                    SortedHostIndex.from(
                        appContext.assets.open("easylist_blocked_hosts.txt").use { it.readBytes() },
                    )
                }.getOrDefault(SortedHostIndex.Empty),
                additionalIndexedHostRules = listOf(
                    runCatching {
                        SortedHostIndex.from(
                            appContext.assets.open("hagezi_blocked_hosts.txt").use {
                                it.readBytes()
                            },
                        )
                    }.getOrDefault(SortedHostIndex.Empty),
                ),
                blockedHostPairs = loadLinesOrEmpty("uassets_blocked_host_pairs.txt"),
                allowedHostPairs = loadLinesOrEmpty(
                    "easylist_allowed_host_pairs.txt",
                    "uassets_allowed_host_pairs.txt",
                ),
                allowedFirstPartyFamilyPairs = loadLinesOrEmpty(
                    "first_party_family_allowed_host_pairs.txt",
                ),
            )
        }.getOrElse { EMPTY_SNAPSHOT.requestBlocker }
    }
    private val advancedRulesFuture = CompletableFuture.supplyAsync {
        runCatching {
            AdvancedFilterRules.parse(readAssetOrEmpty(ADVANCED_ASSET))
        }.getOrDefault(AdvancedFilterRules.Empty)
    }
    private val proceduralRulesFuture = CompletableFuture.supplyAsync {
        runCatching {
            ProceduralCosmeticRules.parse(readAssetOrEmpty(PROCEDURAL_ASSET))
        }.getOrDefault(ProceduralCosmeticRules.Empty)
    }
    @Volatile
    private var readySnapshot: BundledBlockingSnapshot? = null
    private val snapshotFuture = advancedRulesFuture
        .thenCombine(proceduralRulesFuture) { advancedRules, proceduralRules ->
            advancedRules to proceduralRules
        }
        .thenCombine(requestBlockerFuture) { (advancedRules, proceduralRules), requestBlocker ->
            BundledBlockingSnapshot(
                requestBlocker = requestBlocker,
                advancedRules = advancedRules,
                proceduralRules = proceduralRules,
            )
        }
        .exceptionally { EMPTY_SNAPSHOT }
        .thenApply { snapshot ->
            readySnapshot = snapshot
            snapshot
        }

    val isReady: Boolean
        get() = readySnapshot != null

    fun snapshot(): BundledBlockingSnapshot = readySnapshot ?: snapshotFuture.join()

    fun snapshotIfReady(): BundledBlockingSnapshot? = readySnapshot

    fun onReady(action: () -> Unit) {
        snapshotFuture.thenRun(action)
    }

    private fun readAssetOrEmpty(assetName: String): String = runCatching {
        appContext.assets.open(assetName).bufferedReader().use { it.readText() }
    }.getOrDefault("")

    private fun loadLinesOrEmpty(vararg assetNames: String): Sequence<String> =
        assetNames.asSequence().flatMap { assetName ->
            runCatching {
                appContext.assets.open(assetName).bufferedReader().use { it.readLines() }
            }.getOrDefault(emptyList()).asSequence()
        }

    companion object {
        private const val ADVANCED_ASSET = "uassets_advanced_filters.txt"
        private const val PROCEDURAL_ASSET = "uassets_procedural_cosmetic_rules.txt"
        private val EMPTY_SNAPSHOT = BundledBlockingSnapshot(
            requestBlocker = RequestBlocker(emptySequence()),
            advancedRules = AdvancedFilterRules.Empty,
            proceduralRules = ProceduralCosmeticRules.Empty,
        )

        @Volatile
        private var instance: BundledBlockingSnapshotProvider? = null

        fun get(context: Context): BundledBlockingSnapshotProvider =
            instance ?: synchronized(this) {
                instance ?: BundledBlockingSnapshotProvider(context).also { instance = it }
            }
    }
}
