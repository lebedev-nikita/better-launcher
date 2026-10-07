package app.better.launcher

import android.content.Context
import android.content.res.Configuration
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class LocalizationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun localized(language: String): Context {
        val context = instrumentation.targetContext
        val configuration = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        return context.createConfigurationContext(configuration)
    }

    @Test fun englishRussianAndFallbackResourcesPreserveProviderLabelsAndPluralForms() {
        val english = localized("en")
        val russian = localized("ru")
        val fallback = localized("fr")
        assertEquals("Settings", english.getString(R.string.settings))
        assertEquals("Настройки", russian.getString(R.string.settings))
        assertEquals("Settings", fallback.getString(R.string.settings))
        for (context in listOf(english, russian, fallback)) {
            assertTrue(context.getString(R.string.rename_app_title, "Ёлка Café").endsWith("Ёлка Café"))
            assertFalse(context.getString(Weather(12, 0, "Tbilisi").descriptionRes).isBlank())
            assertFalse(context.formatCalendarEventTime(1_700_000_000_000, false).isBlank())
        }
        assertEquals("1 notification", english.resources.getQuantityString(R.plurals.notification_count, 1, 1))
        assertEquals("2 notifications", english.resources.getQuantityString(R.plurals.notification_count, 2, 2))
        assertEquals("1 уведомление", russian.resources.getQuantityString(R.plurals.notification_count, 1, 1))
        assertEquals("2 уведомления", russian.resources.getQuantityString(R.plurals.notification_count, 2, 2))
        assertEquals("5 уведомлений", russian.resources.getQuantityString(R.plurals.notification_count, 5, 5))
        assertEquals("21 уведомление", russian.resources.getQuantityString(R.plurals.notification_count, 21, 21))
        assertTrue(english.formatCalendarEventTime(1_700_000_000_000, true).endsWith("all day"))
        assertTrue(russian.formatCalendarEventTime(1_700_000_000_000, true).endsWith("весь день"))
    }

    @Test fun changingLanguageRecreatesTheLauncherWithTranslatedSettingsAndAlphabet() {
        val previous = AppCompatDelegate.getApplicationLocales()
        fun descendants(view: View): List<View> = listOf(view) +
            if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            try {
                for ((language, title, alphabet) in listOf(Triple("en", "Settings", "App alphabet"), Triple("ru", "Настройки", "Алфавит приложений"))) {
                    instrumentation.runOnMainSync { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language)) }
                    val deadline = SystemClock.uptimeMillis() + 15_000
                    var matched = false
                    while (!matched && SystemClock.uptimeMillis() < deadline) {
                        scenario.onActivity { activity ->
                            if (activity.resources.configuration.locales[0].language == language) {
                                activity.showSettings()
                                val views = descendants(activity.window.decorView)
                                matched = views.filterIsInstance<TextView>().any { it.text.toString() == title }
                                assertEquals(alphabet, views.filterIsInstance<AlphabetRail>().single().alphabetLabel)
                                assertTrue(views.any { it.contentDescription == activity.getString(R.string.back_home) })
                            }
                        }
                        if (!matched) SystemClock.sleep(100)
                    }
                    assertTrue("Settings switch to $language", matched)
                }
            } finally {
                // API 33+ needs a live AppCompat delegate to reach the framework LocaleManager.
                scenario.onActivity { AppCompatDelegate.setApplicationLocales(previous) }
                instrumentation.waitForIdleSync()
            }
        }
    }
}
