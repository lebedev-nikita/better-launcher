package app.better.launcher

import android.content.Context
import android.text.format.DateFormat
import java.text.SimpleDateFormat
import java.util.Date

/** Use the active language for dates and the user's 12/24-hour preference for times. */
fun Context.formatCalendarEventTime(time: Long, allDay: Boolean): String {
    val locale = resources.configuration.locales[0]
    val pattern = DateFormat.getBestDateTimePattern(locale, "EEE d MMM")
    val date = SimpleDateFormat(pattern, locale).format(Date(time))
    return if (allDay) getString(R.string.event_all_day, date)
    else getString(R.string.event_time, date, DateFormat.getTimeFormat(this).format(Date(time)))
}
