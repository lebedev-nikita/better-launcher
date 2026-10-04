package app.better.launcher

import android.content.Context
import android.view.ContextThemeWrapper

/** RemoteViews need framework Views, without the Activity's AppCompat inflater factory. */
internal fun widgetHostContext(context: Context): Context = ContextThemeWrapper(
    context.applicationContext.createConfigurationContext(context.resources.configuration),
    android.R.style.Theme_DeviceDefault,
)
