package dev.sk2andy.materialbrowser.browser.gecko

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeckoExtensionManagerCoordinatorTest {
    @Test
    fun `open loads extensions and binds selected tab context`() = runBlocking {
        val runtime = FakeExtensionRuntime(
            installed = linkedMapOf("addon@example.com" to extension()),
        )
        val coordinator = coordinator(runtime)
        val context = GeckoExtensionManagementContext(profileId = "work", isPrivate = false)

        coordinator.open(context)?.join()

        assertEquals(context, coordinator.state.managementContext)
        assertEquals(listOf("addon@example.com"), coordinator.state.snapshot.extensions.map { it.id })
        assertFalse(coordinator.state.busy)
    }

    @Test
    fun `private tab exposes snapshot but blocks global mutations`() = runBlocking {
        val runtime = FakeExtensionRuntime(
            installed = linkedMapOf("addon@example.com" to extension()),
        )
        val coordinator = coordinator(runtime)
        coordinator.open(
            GeckoExtensionManagementContext(profileId = "private", isPrivate = true),
        )?.join()

        val job = coordinator.setEnabled(coordinator.state.snapshot.extensions.single(), true)

        assertNull(job)
        assertFalse(coordinator.state.canManage)
        assertEquals(0, runtime.enableCount)
    }

    @Test
    fun `install permission prompt is completed by explicit user decision`() = runBlocking {
        val runtime = FakeExtensionRuntime()
        val coordinator = coordinator(runtime)
        coordinator.open(regularContext)?.join()

        val install = requireNotNull(coordinator.install("https://example.com/addon.xpi"))
        yield()

        assertEquals("addon@example.com", coordinator.state.permissionRequest?.extensionId)
        coordinator.completePermissionRequest(granted = true, allowPrivate = true)
        install.join()

        assertEquals(
            GeckoExtensionPermissionDecision(
                grantPermissions = true,
                allowInPrivateBrowsing = true,
            ),
            runtime.lastPermissionDecision,
        )
        assertTrue(coordinator.state.snapshot.extensions.single().enabled)
        assertNull(coordinator.state.permissionRequest)
    }

    @Test
    fun `dismissing overlay denies pending permission prompt`() = runBlocking {
        val runtime = FakeExtensionRuntime()
        val coordinator = coordinator(runtime)
        coordinator.open(regularContext)?.join()

        val install = requireNotNull(coordinator.install("https://example.com/addon.xpi"))
        yield()
        coordinator.dismiss()
        install.join()

        assertEquals(GeckoExtensionPermissionDecision.Denied, runtime.lastPermissionDecision)
        assertNull(coordinator.state.permissionRequest)
    }

    @Test
    fun `runtime permission request is denied while manager is not visible`() = runBlocking {
        val runtime = FakeExtensionRuntime()
        val coordinator = coordinator(runtime)

        coordinator.open(regularContext)?.join()
        coordinator.dismiss()
        val decision = runtime.requestPermission()

        assertEquals(GeckoExtensionPermissionDecision.Denied, decision)
        assertNull(coordinator.state.permissionRequest)
    }

    @Test
    fun `invalid signed xpi address maps to focused manager message`() = runBlocking {
        val coordinator = coordinator(FakeExtensionRuntime())
        coordinator.open(regularContext)?.join()

        coordinator.install("http://example.com/addon.xpi")?.join()

        assertEquals(
            GeckoExtensionManagerMessage.InvalidSignedXpi,
            coordinator.state.message,
        )
    }

    @Test
    fun `page reload is requested after an extension runtime mutation`() = runBlocking {
        val runtime = FakeExtensionRuntime(
            installed = linkedMapOf("addon@example.com" to extension()),
        )
        var reloadCount = 0
        val coordinator = GeckoExtensionManagerCoordinator(
            runtime = runtime,
            scope = this,
            onPageRuntimeChanged = { reloadCount++ },
        )
        coordinator.open(regularContext)?.join()

        coordinator.setEnabled(coordinator.state.snapshot.extensions.single(), true)?.join()

        assertEquals(1, reloadCount)
    }

    @Test
    fun `private permission mutation does not reload the regular page`() = runBlocking {
        val runtime = FakeExtensionRuntime(
            installed = linkedMapOf("addon@example.com" to extension()),
        )
        var reloadCount = 0
        val coordinator = GeckoExtensionManagerCoordinator(
            runtime = runtime,
            scope = this,
            onPageRuntimeChanged = { reloadCount++ },
        )
        coordinator.open(regularContext)?.join()

        coordinator.setAllowedInPrivateBrowsing(
            coordinator.state.snapshot.extensions.single(),
            true,
        )?.join()

        assertEquals(0, reloadCount)
    }

    private fun CoroutineScope.coordinator(runtime: GeckoExtensionRuntime) =
        GeckoExtensionManagerCoordinator(runtime = runtime, scope = this)

    private fun extension() = GeckoExtension(
        id = "addon@example.com",
        name = "Addon",
        version = "1.0",
        enabled = false,
        allowedInPrivateBrowsing = false,
        isBuiltIn = false,
    )

    private class FakeExtensionRuntime(
        private val installed: LinkedHashMap<String, GeckoExtension> = linkedMapOf(),
    ) : GeckoExtensionRuntime {
        private lateinit var prompt: GeckoExtensionPermissionPrompt
        var enableCount = 0
        var lastPermissionDecision: GeckoExtensionPermissionDecision? = null

        override fun setPermissionPrompt(prompt: GeckoExtensionPermissionPrompt) {
            this.prompt = prompt
        }

        override suspend fun listInstalled(): List<GeckoExtension> = installed.values.toList()

        override suspend fun installSignedXpi(
            uri: String,
            installationMethod: String,
        ): GeckoExtension {
            lastPermissionDecision = prompt.decide(
                GeckoExtensionPermissionRequest(
                    kind = GeckoExtensionPermissionRequestKind.Install,
                    extensionId = "addon@example.com",
                    extensionName = "Addon",
                    permissions = listOf("tabs"),
                    origins = listOf("https://example.com/*"),
                ),
            )
            return extension(enabled = true).also { installed[it.id] = it }
        }

        override suspend fun enable(extensionId: String): GeckoExtension {
            enableCount++
            return requireExtension(extensionId).copy(enabled = true).also {
                installed[extensionId] = it
            }
        }

        override suspend fun disable(extensionId: String): GeckoExtension =
            requireExtension(extensionId).copy(enabled = false).also {
                installed[extensionId] = it
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
            installed.remove(extensionId)
        }

        suspend fun requestPermission(): GeckoExtensionPermissionDecision = prompt.decide(
            GeckoExtensionPermissionRequest(
                kind = GeckoExtensionPermissionRequestKind.Optional,
                extensionId = "addon@example.com",
                extensionName = "Addon",
                permissions = listOf("bookmarks"),
                origins = emptyList(),
            ),
        )

        private fun requireExtension(extensionId: String): GeckoExtension =
            requireNotNull(installed[extensionId])

        private fun extension(enabled: Boolean) = GeckoExtension(
            id = "addon@example.com",
            name = "Addon",
            version = "1.0",
            enabled = enabled,
            allowedInPrivateBrowsing = false,
            isBuiltIn = false,
        )
    }

    private companion object {
        val regularContext = GeckoExtensionManagementContext(
            profileId = "default",
            isPrivate = false,
        )
    }
}
