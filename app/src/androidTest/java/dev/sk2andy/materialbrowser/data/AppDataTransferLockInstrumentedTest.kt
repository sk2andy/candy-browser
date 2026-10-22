package dev.sk2andy.materialbrowser.data

import android.content.Context
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDataTransferLockInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val stateDirectory by lazy {
        File(context.applicationInfo.dataDir, AppDataArchiveRules.TRANSFER_STATE_DIRECTORY_NAME)
    }

    @Before
    @After
    fun clearTransferState() {
        stateDirectory.deleteRecursively()
    }

    @Test
    fun activeOwnerCannotBeReplacedOrReleasedByAnotherToken() {
        val token = AppDataTransferLock.activate(context, Process.myPid())

        assertNotNull(token)
        assertTrue(AppDataTransferLock.isActive(context))
        assertNull(AppDataTransferLock.activate(context, Process.myPid()))
        AppDataTransferLock.release(context, "different-token")
        assertTrue(AppDataTransferLock.isActive(context))
        AppDataTransferLock.release(context, requireNotNull(token))
        assertFalse(AppDataTransferLock.isActive(context))
    }

    @Test
    fun concurrentInactiveChecksDoNotReportAnActiveTransfer() {
        assertConcurrentChecks(expectedActive = false)
    }

    @Test
    fun concurrentChecksPreserveTheRealOwnerAndToken() {
        val token = requireNotNull(AppDataTransferLock.activate(context, Process.myPid()))
        try {
            assertConcurrentChecks(expectedActive = true)
            assertNull(AppDataTransferLock.activate(context, Process.myPid()))
            AppDataTransferLock.release(context, "different-token")
            assertTrue(AppDataTransferLock.isActive(context))
        } finally {
            AppDataTransferLock.release(context, token)
        }
        assertFalse(AppDataTransferLock.isActive(context))
    }

    @Test
    fun unavailableGuardRemainsFailClosed() {
        File(stateDirectory, "app_data_transfer.guard").mkdirs()

        assertTrue(AppDataTransferLock.isActive(context))
        assertNull(AppDataTransferLock.activate(context, Process.myPid()))
        assertFalse(
            AppDataTransferLock.claim(context, "unknown-token", Process.myPid(), Process.myPid())
        )
    }

    private fun assertConcurrentChecks(expectedActive: Boolean) {
        val workers = 8
        val barrier = CyclicBarrier(workers)
        val executor = Executors.newFixedThreadPool(workers)
        val unexpected = AtomicInteger()
        try {
            val tasks = List(workers) {
                executor.submit {
                    repeat(64) {
                        barrier.await(5, TimeUnit.SECONDS)
                        if (AppDataTransferLock.isActive(context) != expectedActive)
                            unexpected.incrementAndGet()
                    }
                }
            }
            tasks.forEach { it.get(20, TimeUnit.SECONDS) }
            assertEquals(
                "Concurrent checks changed the observed transfer state",
                0,
                unexpected.get(),
            )
        } finally {
            executor.shutdownNow()
            assertTrue(
                "Transfer checks did not finish",
                executor.awaitTermination(5, TimeUnit.SECONDS),
            )
        }
    }
}
