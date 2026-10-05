package app.better.launcher

import android.app.Dialog
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageView
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.view.inputmethod.EditorInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class LayoutPreviewProvider : AppWidgetProvider()
class ImagePreviewProvider : AppWidgetProvider()
class NoPreviewProvider : AppWidgetProvider()
class BrokenPreviewProvider : AppWidgetProvider()
class NumericPreviewProvider : AppWidgetProvider()
class ResizeReviewProvider : AppWidgetProvider()

class WidgetPickerTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun searchFiltersWidgetAndApplicationNamesAndChoosesTheFilteredProvider() {
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        val host = AppWidgetHost(activity, 1201)
        val before = host.appWidgetIds.toSet()
        var dialog: Dialog? = null
        lateinit var picker: WidgetPickerView
        lateinit var input: EditText
        var chosen: ComponentName? = null
        try {
            instrumentation.runOnMainSync {
                dialog = activity.sheet("Добавить виджет", scrollContent = false) { body, sheet ->
                    picker = WidgetPickerView(activity).apply { onChoose = { chosen = it.provider; sheet.dismiss() } }
                    body.addView(picker, android.widget.LinearLayout.LayoutParams(-1, 0, 1f))
                    input = descendants(picker).filterIsInstance<EditText>().single()
                    // Enter before the background catalog arrives; results must honor this query.
                    input.setText("  IMAGE   WIDGET  ")
                }
            }
            val list = onMain { descendants(picker).filterIsInstance<ListView>().single() }
            val rail = onMain { descendants(picker).filterIsInstance<AlphabetRail>().single() }
            await { list.count == 1 && descendants(list).filterIsInstance<TextView>().any { it.text.toString() == "B00 Image preview" } }
            assertEquals(setOf("B"), onMain { rail.activeLetters })
            await { descendants(list).filterIsInstance<ImageView>().any { it.drawable != null } }
            instrumentation.runOnMainSync { input.setText("ПЛАНЫ") }
            await { list.count == 1 && descendants(list).filterIsInstance<TextView>().any { it.text.toString() == "Планы на сегодня" } }
            assertEquals(setOf("P"), onMain { rail.activeLetters })
            instrumentation.runOnMainSync { input.setText("Widget preview tests") }
            await { list.count == 6 }
            instrumentation.runOnMainSync { input.setText("no-such-widget-987654") }
            await { list.count == 0 && rail.visibility == View.GONE && descendants(picker).filterIsInstance<TextView>().any {
                it.visibility == View.VISIBLE && it.text.toString().startsWith("Виджеты не найдены.")
            } }
            instrumentation.runOnMainSync {
                descendants(picker).single { it.contentDescription == "Очистить поиск виджетов" }.performClick()
            }
            await { list.count >= 6 && rail.visibility == View.VISIBLE }
            assertEquals("", onMain { input.text.toString() })
            assertTrue(onMain { rail.activeLetters.containsAll(listOf("A", "B", "C", "D", "P", "#")) })
            val total = onMain { list.count }
            instrumentation.runOnMainSync { input.setText("  ") }
            assertEquals("Whitespace must restore the entire catalog", total, onMain { list.count })
            instrumentation.runOnMainSync { input.setText("numeric") }
            await { list.count == 1 && descendants(list).filterIsInstance<TextView>().any { it.text.toString() == "000 Numeric preview" } }
            assertEquals(setOf("#"), onMain { rail.activeLetters })
            assertEquals(before, host.appWidgetIds.toSet())
            instrumentation.runOnMainSync { input.onEditorAction(EditorInfo.IME_ACTION_DONE) }
            assertNull("Keyboard Done must not bind or choose a widget", chosen)
            instrumentation.runOnMainSync {
                val row = list.getChildAt(0)
                list.performItemClick(row, 0, list.adapter.getItemId(0))
            }
            assertEquals(ComponentName(instrumentation.context.packageName, NumericPreviewProvider::class.java.name), chosen)
            assertFalse(onMain { dialog?.isShowing == true })
            assertEquals("Searching must not allocate widget IDs", before, host.appWidgetIds.toSet())
        } finally {
            instrumentation.runOnMainSync { dialog?.dismiss(); activity.finish() }
        }
    }

    @Test fun previewsFallbacksAndAlphabetChooseTheDisplayedProviderWithoutBindingDuringBrowse() {
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        val host = AppWidgetHost(activity, 1201)
        val before = host.appWidgetIds.toSet()
        var dialog: Dialog? = null
        lateinit var picker: WidgetPickerView
        var chosen: ComponentName? = null
        try {
            instrumentation.runOnMainSync {
                dialog = activity.sheet("Добавить виджет", scrollContent = false) { body, sheet ->
                    picker = WidgetPickerView(activity).apply { onChoose = { chosen = it.provider; sheet.dismiss() } }
                    body.addView(picker, android.widget.LinearLayout.LayoutParams(-1, 0, 1f))
                }
            }
            await { descendants(picker).filterIsInstance<ListView>().single().count > 0 }
            val list = onMain { descendants(picker).filterIsInstance<ListView>().single() }
            val rail = onMain { descendants(picker).filterIsInstance<AlphabetRail>().single() }
            fun jump(letter: String, label: String): View {
                instrumentation.runOnMainSync { rail.onSelect(letter) }
                // The destination may already be visible below the old section. Wait for the
                // scroll before retaining a recycled row for its asynchronous preview.
                await { list.getChildAt(0)?.let { row -> descendants(row).filterIsInstance<TextView>()
                    .any { it.text.toString() == label } } == true }
                return onMain { (0 until list.childCount).map(list::getChildAt).first { row ->
                    descendants(row).filterIsInstance<TextView>().any { it.text.toString() == label }
                } }
            }
            val layout = jump("A", "A00 Layout preview")
            await { descendants(layout).filterIsInstance<TextView>().any { it.text.toString() == "Layout preview content" } }
            await {
                val last = descendants(layout).filterIsInstance<TextView>().firstOrNull { it.text.toString() == "Last preview line" }
                val bounds = Rect()
                last != null && last.getGlobalVisibleRect(bounds) && bounds.height() > 0
            }
            val image = jump("B", "B00 Image preview")
            await { descendants(image).filterIsInstance<ImageView>().any { it.drawable != null } }
            val missing = jump("C", "C00 No preview")
            await { descendants(missing).filterIsInstance<TextView>().any { it.text.toString() == "Превью недоступно" } }
            val broken = jump("D", "D00 Broken layout")
            await { descendants(broken).filterIsInstance<ImageView>().any { it.drawable != null } }
            jump("#", "000 Numeric preview")
            assertEquals("Browsing must not allocate IDs", before, host.appWidgetIds.toSet())
            assertEquals("#", onMain { rail.letters.last() })
            // Keyboard/TalkBack uses the same section navigation, including the final hash.
            instrumentation.runOnMainSync {
                rail.selected = "D"
                rail.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, null)
            }
            assertTrue(onMain { rail.selected != "D" })
            val selectedRow = jump("B", "B00 Image preview")
            instrumentation.runOnMainSync { val position = list.firstVisiblePosition + list.indexOfChild(selectedRow); list.performItemClick(selectedRow, position, list.adapter.getItemId(position)) }
            assertEquals(ComponentName(instrumentation.context.packageName, ImagePreviewProvider::class.java.name), chosen)
            assertFalse(onMain { dialog?.isShowing == true })
            assertEquals("Picking delegates binding to the controller", before, host.appWidgetIds.toSet())
        } finally {
            instrumentation.runOnMainSync { dialog?.dismiss(); activity.finish() }
        }
    }

    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    private fun <T> onMain(block: () -> T): T {
        var result: T? = null
        instrumentation.runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST") return result as T
    }

    private fun await(predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (onMain(predicate)) return
            SystemClock.sleep(50)
        }
        fail("Widget picker did not reach the expected visible state")
    }
}
