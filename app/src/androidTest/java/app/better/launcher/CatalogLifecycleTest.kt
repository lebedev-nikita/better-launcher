package app.better.launcher

import android.content.pm.LauncherApps
import android.os.Process
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class CatalogLifecycleTest {
    @Test fun latePackageNotificationAfterDestroyLeavesStoredFavoritesIntact() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var activity: MainActivity
        lateinit var callback: LauncherApps.Callback
        scenario.onActivity {
            activity = it
            callback = MainActivity::class.java.getDeclaredField("packageCallback").apply { isAccessible = true }.get(it) as LauncherApps.Callback
        }
        val loading = MainActivity::class.java.getDeclaredField("loadingApps").apply { isAccessible = true }
        val deadline = SystemClock.uptimeMillis() + 15000
        var ready = false
        while (!ready && SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync { ready = !loading.getBoolean(activity) }
            if (!ready) SystemClock.sleep(50)
        }
        assertTrue("Initial catalog load must finish", ready)
        val favorites = activity.store.favorites
        scenario.close()
        instrumentation.runOnMainSync {
            assertTrue(activity.isDestroyed)
            // LauncherApps may already have queued a callback before unregisterCallback.
            callback.onPackageChanged("com.android.camera2", Process.myUserHandle())
            callback.onPackagesAvailable(arrayOf("com.android.camera2"), Process.myUserHandle(), false)
        }
        assertEquals(favorites, LauncherStore(instrumentation.targetContext).favorites)
    }
}
