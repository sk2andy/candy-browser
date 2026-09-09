package dev.sk2andy.materialbrowser.browser.gecko

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
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
    fun `private tab neither loads inventory nor allows global mutations`() = runBlocking {
        val runtime = FakeExtensionRuntime(
            installed = linkedMapOf("addon@example.com" to extension()),
        )
        val coordinator = coordinator(runtime)
        coordinator.open(
            GeckoExtensionManagementContext(profileId = "private", isPrivate = true),
        )?.join()

        val job = coordinator.setEnabled(extension(), true)

        assertNull(job)
        assertFalse(coordinator.state.canManage)
        assertTrue(coordinator.state.snapshot.extensions.isEmpty())
        assertEquals(0, runtime.listInstalledCount)
        assertEquals(0, runtime.enableCount)
    }

    @Test
    fun `regular inventory finishing after private open stays hidden`() = runBlocking {
        val listInstalledGate = CompletableDeferred<Unit>()
        val runtime = FakeExtensionRuntime(
            installed = linkedMapOf("addon@example.com" to extension()),
            listInstalledGate = listInstalledGate,
        )
        val coordinator = coordinator(runtime)
        val regularLoad = requireNotNull(coordinator.open(regularContext))
        yield()

        coordinator.open(
            GeckoExtensionManagementContext(profileId = "private", isPrivate = true),
        )
        listInstalledGate.complete(Unit)
        regularLoad.join()

        assertTrue(coordinator.state.managementContext.isPrivate)
        assertTrue(coordinator.state.snapshot.extensions.isEmpty())
    }

    @Test
    fun `options picker opens exact installed moz extension origin`() = runBlocking {
        val installed = extension(
            enabled = true,
            baseUrl = "moz-extension://installed-origin/",
            optionsPageUrl = "moz-extension://installed-origin/options/../settings.html",
        )
        val runtime = FakeExtensionRuntime(linkedMapOf(installed.id to installed))
        var openedTarget: GeckoExtensionOptionsTarget? = null
        val coordinator = GeckoExtensionManagerCoordinator(
            runtime = runtime,
            scope = this,
            onOpenOptionsPage = { target ->
                openedTarget = target
                true
            },
        )
        coordinator.open(
            regularContext,
            GeckoExtensionManagerPresentation.Options,
        )?.join()

        requireNotNull(coordinator.openOptionsPage(installed.id)).join()

        assertEquals(
            GeckoExtensionOptionsTarget(
                extensionId = installed.id,
                title = "Addon",
                url = "moz-extension://installed-origin/settings.html",
            ),
            openedTarget,
        )
        assertEquals(2, runtime.listInstalledCount)
    }

    @Test
    fun `options picker rejects foreign origin stale id and private context`() = runBlocking {
        val installed = extension(
            enabled = true,
            baseUrl = "moz-extension://installed-origin/",
            optionsPageUrl = "moz-extension://foreign-origin/settings.html",
        )
        val runtime = FakeExtensionRuntime(linkedMapOf(installed.id to installed))
        var openCount = 0
        val coordinator = GeckoExtensionManagerCoordinator(
            runtime = runtime,
            scope = this,
            onOpenOptionsPage = {
                openCount++
                true
            },
        )
        coordinator.open(
            regularContext,
            GeckoExtensionManagerPresentation.Options,
        )?.join()

        requireNotNull(coordinator.openOptionsPage(installed.id)).join()
        requireNotNull(coordinator.openOptionsPage("missing@example.com")).join()
        coordinator.open(
            GeckoExtensionManagementContext(profileId = "private", isPrivate = true),
            GeckoExtensionManagerPresentation.Options,
        )
        assertNull(coordinator.openOptionsPage(installed.id))
        assertEquals(0, openCount)
    }

    @Test
    fun `options picker rejects extension disabled after list opened`() = runBlocking {
        val installed = extension(
            enabled = true,
            baseUrl = "moz-extension://installed-origin/",
            optionsPageUrl = "moz-extension://installed-origin/options.html",
        )
        val runtime = FakeExtensionRuntime(linkedMapOf(installed.id to installed))
        var openCount = 0
        val coordinator = GeckoExtensionManagerCoordinator(
            runtime = runtime,
            scope = this,
            onOpenOptionsPage = {
                openCount++
                true
            },
        )
        coordinator.open(regularContext, GeckoExtensionManagerPresentation.Options)?.join()
        runtime.replaceInstalled(installed.copy(enabled = false))

        requireNotNull(coordinator.openOptionsPage(installed.id)).join()

        assertEquals(0, openCount)
        assertFalse(requireNotNull(coordinator.state.snapshot.extension(installed.id)).enabled)
    }

    @Test
    fun `options picker rejects extension uninstalled after list opened`() = runBlocking {
        val installed = extension(
            enabled = true,
            baseUrl = "moz-extension://installed-origin/",
            optionsPageUrl = "moz-extension://installed-origin/options.html",
        )
        val runtime = FakeExtensionRuntime(linkedMapOf(installed.id to installed))
        var openCount = 0
        val coordinator = GeckoExtensionManagerCoordinator(
            runtime = runtime,
            scope = this,
            onOpenOptionsPage = {
                openCount++
                true
            },
        )
        coordinator.open(regularContext, GeckoExtensionManagerPresentation.Options)?.join()
        runtime.removeInstalled(installed.id)

        requireNotNull(coordinator.openOptionsPage(installed.id)).join()

        assertEquals(0, openCount)
        assertTrue(coordinator.state.snapshot.extensions.isEmpty())
    }

    @Test
    fun `options picker uses current origin after installed origin changes`() = runBlocking {
        val installed = extension(
            enabled = true,
            baseUrl = "moz-extension://old-origin/",
            optionsPageUrl = "moz-extension://old-origin/options.html",
        )
        val runtime = FakeExtensionRuntime(linkedMapOf(installed.id to installed))
        var openedTarget: GeckoExtensionOptionsTarget? = null
        val coordinator = GeckoExtensionManagerCoordinator(
            runtime = runtime,
            scope = this,
            onOpenOptionsPage = { target ->
                openedTarget = target
                true
            },
        )
        coordinator.open(regularContext, GeckoExtensionManagerPresentation.Options)?.join()
        runtime.replaceInstalled(
            installed.copy(
                baseUrl = "moz-extension://current-origin/",
                optionsPageUrl = "moz-extension://current-origin/options.html",
            ),
        )

        requireNotNull(coordinator.openOptionsPage(installed.id)).join()

        assertEquals("moz-extension://current-origin/options.html", openedTarget?.url)
        assertEquals(installed.id, openedTarget?.extensionId)
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

    private fun extension(
        enabled: Boolean = false,
        baseUrl: String? = null,
        optionsPageUrl: String? = null,
    ) = GeckoExtension(
        id = "addon@example.com",
        name = "Addon",
        version = "1.0",
        enabled = enabled,
        allowedInPrivateBrowsing = false,
        isBuiltIn = false,
        baseUrl = baseUrl,
        optionsPageUrl = optionsPageUrl,
    )

    private class FakeExtensionRuntime(
        private val installed: LinkedHashMap<String, GeckoExtension> = linkedMapOf(),
        private val listInstalledGate: CompletableDeferred<Unit>? = null,
    ) : GeckoExtensionRuntime {
        private lateinit var prompt: GeckoExtensionPermissionPrompt
        var enableCount = 0
        var listInstalledCount = 0
        var lastPermissionDecision: GeckoExtensionPermissionDecision? = null

        override fun setPermissionPrompt(prompt: GeckoExtensionPermissionPrompt) {
            this.prompt = prompt
        }

        override suspend fun listInstalled(): List<GeckoExtension> {
            listInstalledCount++
            listInstalledGate?.await()
            return installed.values.toList()
        }

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

        fun replaceInstalled(extension: GeckoExtension) {
            installed[extension.id] = extension
        }

        fun removeInstalled(extensionId: String) {
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
