package dev.sk2andy.materialbrowser.browser

import android.Manifest
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class BrowserMediaPlaybackServiceInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @After
    fun releaseService() {
        instrumentation.runOnMainSync { BrowserMediaPlaybackService.release(context) }
        awaitServiceState(expected = false)
    }

    @Test
    fun authenticatedMediaControllerConnectsAndRoutesAnExactOwnerCommand() {
        val commands = mutableListOf<Pair<GeckoMediaPlaybackOwner, GeckoMediaPlaybackCommand>>()
        val owner = owner()

        publish(publication(owner), commands)
        SystemClock.sleep(100L)
        assertTrue(isServiceRunning())

        instrumentation.runOnMainSync {
            val state = BrowserMediaPlaybackService.serviceStateForTesting()
            assertEquals(1, state.sessionCount)
            assertEquals(Player.STATE_READY, state.playbackState)
        }

        val controller = MediaController.Builder(
            context,
            SessionToken(context, ComponentName(context, BrowserMediaPlaybackService::class.java)),
        ).buildAsync().get(5L, TimeUnit.SECONDS)
        try {
            instrumentation.runOnMainSync { controller.pause() }
            instrumentation.waitForIdleSync()
        } finally {
            instrumentation.runOnMainSync { controller.release() }
        }

        assertEquals(listOf(owner to GeckoMediaPlaybackCommand.Pause), commands)
        assertFalse(
            BrowserMediaControllerAuthRules.accepts(
                ownPackageName = context.packageName,
                controllerPackageName = "com.example.untrusted",
                controllerUid = 12_345,
                isTrustedSystemController = false,
                isMediaNotificationController = false,
            ),
        )
    }

    @Test
    fun playingPublicationCreatesMediaNotificationAndForegroundServiceWhenAllowed() {
        val commands = mutableListOf<Pair<GeckoMediaPlaybackOwner, GeckoMediaPlaybackCommand>>()
        publish(publication(owner()), commands)

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val deadline = SystemClock.uptimeMillis() + 5_000L
        while (SystemClock.uptimeMillis() < deadline) {
            if (
                notificationManager.getNotificationChannel(BrowserMediaPlaybackService.CHANNEL_ID)?.id ==
                BrowserMediaPlaybackService.CHANNEL_ID
            ) {
                break
            }
            SystemClock.sleep(50L)
        }
        assertEquals(
            BrowserMediaPlaybackService.CHANNEL_ID,
            notificationManager.getNotificationChannel(BrowserMediaPlaybackService.CHANNEL_ID)?.id,
        )
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val notificationDeadline = SystemClock.uptimeMillis() + 5_000L
        while (SystemClock.uptimeMillis() < notificationDeadline) {
            if (hasForegroundMediaNotification() && isMediaServiceForeground()) return
            SystemClock.sleep(50L)
        }
        val diagnostics = mediaNotificationDiagnostics()
        assertTrue(diagnostics, hasForegroundMediaNotification())
        assertTrue(diagnostics, isMediaServiceForeground())
    }

    @Test
    fun activityTeardownStopsExactOwnerBeforeTaskRemovalAndLeavesLaterRemovalIdempotent() {
        val commands = mutableListOf<Pair<GeckoMediaPlaybackOwner, GeckoMediaPlaybackCommand>>()
        val owner = owner()

        publish(publication(owner), commands)
        instrumentation.runOnMainSync {
            BrowserMediaPlaybackService.stopAndClear(context)
            BrowserMediaPlaybackService.taskRemovedForTesting()
            val state = BrowserMediaPlaybackService.serviceStateForTesting()
            assertNull(state.publication)
            assertFalse(state.hasCommandSink)
            state.playbackState?.let { assertEquals(Player.STATE_IDLE, it) }
        }
        awaitServiceState(expected = false)

        assertEquals(listOf(owner to GeckoMediaPlaybackCommand.Stop), commands)
    }

    @Test
    fun rejectedServiceStartIgnoresReentrantPublicationFromExactOwnerStop() {
        val commands = mutableListOf<Pair<GeckoMediaPlaybackOwner, GeckoMediaPlaybackCommand>>()
        val owner = owner()
        val publication = publication(owner)

        instrumentation.runOnMainSync {
            BrowserMediaPlaybackService.publish(
                context = context,
                publication = publication,
                onCommand = { commandOwner, command ->
                    commands += commandOwner to command
                    BrowserMediaPlaybackService.replacePublication(
                        context = context,
                        publication = publication,
                        onCommand = { nestedOwner, nestedCommand ->
                            commands += nestedOwner to nestedCommand
                        },
                        mayStartService = false,
                    )
                },
                mayStartService = false,
            )
            val state = BrowserMediaPlaybackService.serviceStateForTesting()
            assertNull(state.publication)
            assertFalse(state.hasCommandSink)
            assertNull(state.serviceInstanceId)
        }

        assertEquals(listOf(owner to GeckoMediaPlaybackCommand.Stop), commands)
    }

    @Test
    fun unexpectedServiceDetachStopsExactOwnerAndAtomicallyClearsRegistry() {
        val commands = mutableListOf<Pair<GeckoMediaPlaybackOwner, GeckoMediaPlaybackCommand>>()
        val owner = owner()

        publish(publication(owner), commands)
        instrumentation.runOnMainSync {
            BrowserMediaPlaybackService.unexpectedServiceDetachForTesting()
            val state = BrowserMediaPlaybackService.serviceStateForTesting()
            assertNull(state.publication)
            assertFalse(state.hasCommandSink)
        }
        context.stopService(Intent(context, BrowserMediaPlaybackService::class.java))
        awaitServiceState(expected = false)

        assertEquals(listOf(owner to GeckoMediaPlaybackCommand.Stop), commands)
    }

    @Test
    fun privatePublicationClearRemovesRuntimeStateAndCommandRoute() {
        val commands = mutableListOf<Pair<GeckoMediaPlaybackOwner, GeckoMediaPlaybackCommand>>()
        val owner = owner()

        publish(publication(owner), commands)
        instrumentation.runOnMainSync {
            BrowserMediaPlaybackService.clearForPrivateContext(context)
            val state = BrowserMediaPlaybackService.serviceStateForTesting()
            assertNull(state.publication)
            assertFalse(state.hasCommandSink)
            assertEquals(Player.STATE_IDLE, state.playbackState)
            BrowserMediaPlaybackService.dispatchForTesting(owner, GeckoMediaPlaybackCommand.Play)
        }

        assertTrue(commands.isEmpty())
    }

    @Test
    fun pausedInitialPublicationDefersServiceWithoutStoppingItsExactOwner() {
        val commands = mutableListOf<Pair<GeckoMediaPlaybackOwner, GeckoMediaPlaybackCommand>>()
        val owner = owner()
        val pausedPublication = publication(owner).copy(
            snapshot = publication(owner).snapshot.copy(isPlaying = false),
        )

        instrumentation.runOnMainSync {
            BrowserMediaPlaybackService.publish(
                context = context,
                publication = pausedPublication,
                onCommand = { commandOwner, command -> commands += commandOwner to command },
                mayStartService = true,
            )
            val state = BrowserMediaPlaybackService.serviceStateForTesting()
            assertEquals(owner, state.publication?.snapshot?.owner)
            assertTrue(state.hasCommandSink)
            assertNull(state.sessionCount)
        }

        assertTrue(commands.isEmpty())
    }

    @Test
    fun pictureInPictureRestoreReplacesPublicationWithoutIdlingOrDuplicatingSession() {
        val commands = mutableListOf<Pair<GeckoMediaPlaybackOwner, GeckoMediaPlaybackCommand>>()
        val backgroundOwner = owner(tabId = "background", engineSessionId = 3L)
        val pictureInPictureOwner = owner(tabId = "pip", engineSessionId = 4L)

        publish(publication(backgroundOwner), commands)
        val playbackStates = mutableListOf<Int>()
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                playbackStates += playbackState
            }
        }
        instrumentation.runOnMainSync {
            assertTrue(BrowserMediaPlaybackService.addPlayerListenerForTesting(listener))
            val serviceInstanceId = BrowserMediaPlaybackService.serviceStateForTesting().serviceInstanceId
            BrowserMediaPlaybackService.publish(
                context = context,
                publication = publication(pictureInPictureOwner, isPictureInPictureOwner = true),
                onCommand = { commandOwner, command -> commands += commandOwner to command },
                mayStartService = true,
            )
            assertEquals(Player.STATE_READY, BrowserMediaPlaybackService.serviceStateForTesting().playbackState)
            assertEquals(1, BrowserMediaPlaybackService.serviceStateForTesting().sessionCount)

            BrowserMediaPlaybackService.restoreAfterPictureInPicture(
                context = context,
                publication = publication(backgroundOwner),
                onCommand = { commandOwner, command -> commands += commandOwner to command },
                mayStartService = true,
            )
            val restored = BrowserMediaPlaybackService.serviceStateForTesting()
            assertEquals(backgroundOwner, restored.publication?.snapshot?.owner)
            assertEquals(Player.STATE_READY, restored.playbackState)
            assertEquals(1, restored.sessionCount)
            assertEquals(serviceInstanceId, restored.serviceInstanceId)
            BrowserMediaPlaybackService.removePlayerListenerForTesting(listener)
        }
        assertFalse(playbackStates.contains(Player.STATE_IDLE))
    }

    private fun publish(
        publication: GeckoMediaPlaybackPublication,
        commands: MutableList<Pair<GeckoMediaPlaybackOwner, GeckoMediaPlaybackCommand>>,
    ) {
        instrumentation.runOnMainSync {
            BrowserMediaPlaybackService.publish(
                context = context,
                publication = publication,
                onCommand = { owner, command -> commands += owner to command },
                mayStartService = true,
            )
        }
        awaitServiceState(expected = true)
        awaitServiceReady()
    }

    private fun publication(
        owner: GeckoMediaPlaybackOwner,
        isPictureInPictureOwner: Boolean = false,
    ): GeckoMediaPlaybackPublication = GeckoMediaPlaybackPublication(
        snapshot = requireNotNull(
            GeckoMediaPlaybackRules.snapshot(
                BrowserMediaState(
                    tabId = owner.tabId,
                    title = "Song",
                    origin = "media.example",
                    kind = BrowserMediaKind.Audio,
                    isPlaying = true,
                    currentPositionMillis = 1_000L,
                    durationMillis = 10_000L,
                    playbackRate = 1f,
                    sourceUrl = "https://media.example/private.mp3",
                    contentType = "audio/mpeg",
                    posterUrl = "https://media.example/artwork.png",
                ),
                owner,
            ),
        ),
        isPrivate = false,
        isProfileLocked = false,
        isAppDataTransferActive = false,
        isPictureInPictureOwner = isPictureInPictureOwner,
    )

    private fun owner(
        tabId: String = "media-tab",
        engineSessionId: Long = 7L,
    ) = GeckoMediaPlaybackOwner(
        tabId = tabId,
        engineSessionId = engineSessionId,
        navigationGeneration = 3,
    )

    private fun awaitServiceState(expected: Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5_000L
        while (SystemClock.uptimeMillis() < deadline) {
            if (isServiceRunning() == expected) return
            SystemClock.sleep(50L)
        }
        assertEquals(expected, isServiceRunning())
    }

    private fun awaitServiceReady() {
        val deadline = SystemClock.uptimeMillis() + 5_000L
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            instrumentation.runOnMainSync {
                ready = BrowserMediaPlaybackService.serviceStateForTesting().sessionCount == 1
            }
            if (ready) return
            SystemClock.sleep(50L)
        }
        var sessionCount: Int? = null
        instrumentation.runOnMainSync {
            sessionCount = BrowserMediaPlaybackService.serviceStateForTesting().sessionCount
        }
        assertEquals(1, sessionCount)
    }

    @Suppress("DEPRECATION")
    private fun isServiceRunning(): Boolean =
        context.getSystemService(ActivityManager::class.java)
            .getRunningServices(Int.MAX_VALUE)
            .any { service ->
                service.service.className == BrowserMediaPlaybackService::class.java.name
            }

    @Suppress("DEPRECATION")
    private fun isMediaServiceForeground(): Boolean =
        context.getSystemService(ActivityManager::class.java)
            .getRunningServices(Int.MAX_VALUE)
            .any { service ->
                service.service.className == BrowserMediaPlaybackService::class.java.name &&
                    service.foreground
            }

    private fun hasForegroundMediaNotification(): Boolean =
        context.getSystemService(NotificationManager::class.java)
            .activeNotifications
            .any { notification ->
                notification.packageName == context.packageName &&
                    notification.notification.channelId == BrowserMediaPlaybackService.CHANNEL_ID &&
                    notification.notification.category == Notification.CATEGORY_TRANSPORT
            }

    private fun mediaNotificationDiagnostics(): String {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val notifications = notificationManager.activeNotifications
            .filter { notification -> notification.packageName == context.packageName }
            .map { notification ->
                "id=${notification.id},channel=${notification.notification.channelId}," +
                    "flags=${notification.notification.flags},category=${notification.notification.category}"
            }
        val services = context.getSystemService(ActivityManager::class.java)
            .getRunningServices(Int.MAX_VALUE)
            .filter { service ->
                service.service.className == BrowserMediaPlaybackService::class.java.name
            }
            .map { service -> "foreground=${service.foreground},pid=${service.pid}" }
        var trace = emptyList<BrowserMediaLifecycleTraceEvent>()
        instrumentation.runOnMainSync {
            trace = BrowserMediaPlaybackService.lifecycleTraceForTesting()
        }
        return "trace=$trace,channel=" +
            "${notificationManager.getNotificationChannel(BrowserMediaPlaybackService.CHANNEL_ID)?.id}," +
            "notifications=$notifications,services=$services"
    }
}
