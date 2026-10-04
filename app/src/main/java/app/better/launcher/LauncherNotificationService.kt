package app.better.launcher

import android.app.Notification
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** In-memory only. Notification contents are never persisted or sent over the network. */
object NotificationHub {
    private val active = linkedMapOf<String, StatusBarNotification>()
    var service: LauncherNotificationService? = null
    var onChanged: (() -> Unit)? = null
    fun notifications(packageName: String, user: UserHandle) = active.values.filter { it.packageName == packageName && it.user == user && it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }.sortedByDescending { it.postTime }
    fun replace(items: Array<StatusBarNotification>) { active.clear(); items.forEach { active[it.key] = it }; notifyChanged() }
    fun put(item: StatusBarNotification) { active[item.key] = item; notifyChanged() }
    fun remove(key: String) { active.remove(key); notifyChanged() }
    fun clear() { active.clear(); notifyChanged() }
    private fun notifyChanged() { Handler(Looper.getMainLooper()).post { onChanged?.invoke() } }
}

class LauncherNotificationService : NotificationListenerService() {
    override fun onListenerConnected() {
        NotificationHub.service = this
        NotificationHub.replace(activeNotifications ?: emptyArray())
    }
    override fun onListenerDisconnected() { NotificationHub.service = null; NotificationHub.clear() }
    override fun onNotificationPosted(sbn: StatusBarNotification) { NotificationHub.put(sbn) }
    override fun onNotificationRemoved(sbn: StatusBarNotification) { NotificationHub.remove(sbn.key) }
    override fun onDestroy() { NotificationHub.service = null; NotificationHub.clear(); super.onDestroy() }
}
