package dev.sk2andy.materialbrowser.capsule

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.SiteCapsuleActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CapsuleWindowLauncherInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun sameCapsuleHasStableDocumentIdentityAndOtherCapsulesHaveSeparateTasks() {
        val capsule = SiteCapsule(
            id = "04a74ad8-7533-460c-bfbf-a135968940d5",
            name = "Mail",
            startUrl = "https://mail.example",
            profileId = "candy",
            opensInOwnWindow = true,
            createdAtMillis = 1L,
            updatedAtMillis = 1L,
        )
        val first = CapsuleWindowLauncher.intent(context, capsule, "https://mail.example/inbox")
        val same = CapsuleWindowLauncher.intent(context, capsule)
        val other = CapsuleWindowLauncher.intent(
            context,
            capsule.copy(id = "0f574fca-8727-4392-b34f-c30a1ee5a058"),
        )
        @Suppress("DEPRECATION")
        val info = context.packageManager.getActivityInfo(
            requireNotNull(first.component),
            PackageManager.GET_META_DATA,
        )

        assertEquals(SiteCapsuleActivity::class.java.name, first.component?.className)
        assertEquals(first.data, same.data)
        assertNotEquals(first.data, other.data)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_DOCUMENT, first.flags)
        assertEquals(ActivityInfo.DOCUMENT_LAUNCH_INTO_EXISTING, info.documentLaunchMode)
        assertFalse(info.exported)
        assertEquals("https://mail.example/inbox", first.getStringExtra(CapsuleWindowLauncher.EXTRA_TARGET_URL))
    }
}
