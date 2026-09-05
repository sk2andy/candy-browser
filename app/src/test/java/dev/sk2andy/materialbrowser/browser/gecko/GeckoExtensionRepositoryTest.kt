package dev.sk2andy.materialbrowser.browser.gecko

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeckoExtensionRepositoryTest {
    private val regularContext = GeckoExtensionManagementContext(
        profileId = "default",
        isPrivate = false,
    )

    @Test
    fun `refresh publishes canonical runtime snapshot`() = runBlocking {
        val runtime = FakeGeckoExtensionRuntime(
            installed = linkedMapOf(
                "z@example.com" to extension(id = "z@example.com", name = "Zulu"),
                "a@example.com" to extension(id = "a@example.com", name = "Alpha"),
            ),
        )
        val repository = repository(runtime)

        val result = repository.refresh()

        assertTrue(result is GeckoExtensionReadResult.Loaded)
        assertEquals(
            listOf("a@example.com", "z@example.com"),
            repository.snapshot().extensions.map(GeckoExtension::id),
        )
    }

    @Test
    fun `private management context cannot install global extension`() = runBlocking {
        val runtime = FakeGeckoExtensionRuntime()
        val repository = repository(runtime)

        val result = repository.installSignedXpi(
            rawUri = "https://example.com/addon.xpi",
            context = regularContext.copy(isPrivate = true),
        )

        assertEquals(
            GeckoExtensionMutationResult.Rejected(
                GeckoExtensionRejection.PrivateManagementContext,
            ),
            result,
        )
        assertEquals(0, runtime.installCount)
    }

    @Test
    fun `signed install uses manager method and updates snapshot`() = runBlocking {
        val runtime = FakeGeckoExtensionRuntime()
        val repository = repository(runtime)

        val result = repository.installSignedXpi(
            rawUri = "https://example.com/releases/addon.xpi",
            context = regularContext,
        )

        assertTrue(result is GeckoExtensionMutationResult.Applied)
        assertEquals("https://example.com/releases/addon.xpi", runtime.lastInstallUri)
        assertEquals("manager", runtime.lastInstallationMethod)
        assertEquals(listOf("installed@example.com"), repository.snapshot().extensions.map { it.id })
    }

    @Test
    fun `enable disable private opt in and uninstall follow current extension`() = runBlocking {
        val runtime = FakeGeckoExtensionRuntime(
            installed = linkedMapOf("addon@example.com" to extension(enabled = false)),
        )
        val repository = repository(runtime)
        repository.refresh()

        assertTrue(repository.enable("addon@example.com", regularContext).isApplied())
        assertTrue(requireNotNull(repository.snapshot().extension("addon@example.com")).enabled)

        assertTrue(
            repository.setAllowedInPrivateBrowsing(
                extensionId = "addon@example.com",
                allowed = true,
                context = regularContext,
            ).isApplied(),
        )
        assertTrue(
            requireNotNull(repository.snapshot().extension("addon@example.com"))
                .allowedInPrivateBrowsing,
        )

        assertTrue(repository.disable("addon@example.com", regularContext).isApplied())
        assertFalse(requireNotNull(repository.snapshot().extension("addon@example.com")).enabled)

        assertTrue(repository.update("addon@example.com", regularContext).isApplied())
        assertEquals(
            "2.0",
            requireNotNull(repository.snapshot().extension("addon@example.com")).version,
        )

        assertTrue(repository.uninstall("addon@example.com", regularContext).isApplied())
        assertTrue(repository.snapshot().extensions.isEmpty())
    }

    @Test
    fun `built in extension cannot be changed by user manager`() = runBlocking {
        val runtime = FakeGeckoExtensionRuntime(
            installed = linkedMapOf(
                "candy@example.com" to extension(
                    id = "candy@example.com",
                    isBuiltIn = true,
                ),
            ),
        )
        val repository = repository(runtime)
        repository.refresh()

        val result = repository.disable("candy@example.com", regularContext)

        assertEquals(
            GeckoExtensionMutationResult.Rejected(
                GeckoExtensionRejection.BuiltInExtensionProtected,
            ),
            result,
        )
        assertEquals(0, runtime.disableCount)
    }

    @Test
    fun `prompt failure denies install permissions safely`() = runBlocking {
        val runtime = FakeGeckoExtensionRuntime()
        repository(runtime) { error("UI disappeared") }

        val decision = runtime.requestDecision(permissionRequest())

        assertEquals(GeckoExtensionPermissionDecision.Denied, decision)
    }

    @Test
    fun `malformed permission request is denied before reaching UI`() = runBlocking {
        val runtime = FakeGeckoExtensionRuntime()
        var promptCount = 0
        repository(runtime) {
            promptCount++
            GeckoExtensionPermissionDecision(grantPermissions = true)
        }

        val decision = runtime.requestDecision(
            permissionRequest().copy(extensionId = "bad\nidentifier"),
        )

        assertEquals(GeckoExtensionPermissionDecision.Denied, decision)
        assertEquals(0, promptCount)
    }

    @Test
    fun `runtime failure returns typed failure and preserves snapshot`() = runBlocking {
        val runtime = FakeGeckoExtensionRuntime(
            installed = linkedMapOf("addon@example.com" to extension()),
        )
        val repository = repository(runtime)
        repository.refresh()
        runtime.enableFailure = IllegalStateException("signature disabled")

        val result = repository.enable("addon@example.com", regularContext)

        assertTrue(result is GeckoExtensionMutationResult.Failed)
        assertFalse(requireNotNull(repository.snapshot().extension("addon@example.com")).enabled)
    }

    private fun repository(
        runtime: FakeGeckoExtensionRuntime,
        prompt: GeckoExtensionPermissionPrompt = GeckoExtensionPermissionPrompt {
            GeckoExtensionPermissionDecision(grantPermissions = true)
        },
    ) = GeckoExtensionRepository(runtime, prompt)

    private fun extension(
        id: String = "addon@example.com",
        name: String = "Addon",
        enabled: Boolean = false,
        allowedInPrivateBrowsing: Boolean = false,
        isBuiltIn: Boolean = false,
    ) = GeckoExtension(
        id = id,
        name = name,
        version = "1.0",
        enabled = enabled,
        allowedInPrivateBrowsing = allowedInPrivateBrowsing,
        isBuiltIn = isBuiltIn,
    )

    private fun permissionRequest() = GeckoExtensionPermissionRequest(
        kind = GeckoExtensionPermissionRequestKind.Install,
        extensionId = "addon@example.com",
        extensionName = "Addon",
        permissions = listOf("tabs"),
        origins = listOf("https://example.com/*"),
    )

    private fun GeckoExtensionMutationResult.isApplied(): Boolean =
        this is GeckoExtensionMutationResult.Applied

    private class FakeGeckoExtensionRuntime(
        val installed: LinkedHashMap<String, GeckoExtension> = linkedMapOf(),
    ) : GeckoExtensionRuntime {
        private lateinit var permissionPrompt: GeckoExtensionPermissionPrompt
        var installCount = 0
        var disableCount = 0
        var lastInstallUri: String? = null
        var lastInstallationMethod: String? = null
        var enableFailure: Throwable? = null

        override fun setPermissionPrompt(prompt: GeckoExtensionPermissionPrompt) {
            permissionPrompt = prompt
        }

        override suspend fun listInstalled(): List<GeckoExtension> = installed.values.toList()

        override suspend fun installSignedXpi(
            uri: String,
            installationMethod: String,
        ): GeckoExtension {
            installCount++
            lastInstallUri = uri
            lastInstallationMethod = installationMethod
            return GeckoExtension(
                id = "installed@example.com",
                name = "Installed Addon",
                version = "1.0",
                enabled = true,
                allowedInPrivateBrowsing = false,
                isBuiltIn = false,
            ).also { installed[it.id] = it }
        }

        override suspend fun enable(extensionId: String): GeckoExtension {
            enableFailure?.let { throw it }
            return requireExtension(extensionId).copy(enabled = true).also {
                installed[extensionId] = it
            }
        }

        override suspend fun disable(extensionId: String): GeckoExtension {
            disableCount++
            return requireExtension(extensionId).copy(enabled = false).also {
                installed[extensionId] = it
            }
        }

        override suspend fun update(extensionId: String): GeckoExtension =
            requireExtension(extensionId).copy(version = "2.0").also {
                installed[extensionId] = it
            }

        override suspend fun setAllowedInPrivateBrowsing(
            extensionId: String,
            allowed: Boolean,
        ): GeckoExtension = requireExtension(extensionId).copy(
            allowedInPrivateBrowsing = allowed,
        ).also { installed[extensionId] = it }

        override suspend fun uninstall(extensionId: String) {
            checkNotNull(installed.remove(extensionId))
        }

        suspend fun requestDecision(
            request: GeckoExtensionPermissionRequest,
        ): GeckoExtensionPermissionDecision = permissionPrompt.decide(request)

        private fun requireExtension(extensionId: String): GeckoExtension =
            requireNotNull(installed[extensionId])
    }
}
