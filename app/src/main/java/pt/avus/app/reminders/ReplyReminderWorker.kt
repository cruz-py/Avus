package pt.avus.app.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import pt.avus.app.MainActivity
import pt.avus.app.R
import pt.avus.app.data.FollowUp
import pt.avus.app.data.Repository
import java.util.concurrent.TimeUnit

/**
 * Once a day, posts a notification when homes have not answered for a week, so the family knows
 * it is time to call. Each unanswered contact is reminded about once (again only if it changes).
 * Everything runs on the device; nothing is sent anywhere.
 */
class ReplyReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!canNotify(applicationContext)) return Result.success()
        val repo = Repository(applicationContext)
        val now = System.currentTimeMillis()
        val contacts = repo.loadContacts()
        val reminded = repo.loadReminded()
        if (FollowUp.dueForReminder(contacts, reminded, now).isEmpty()) return Result.success()

        val overdue = FollowUp.overdue(contacts, now)
        val names = repo.loadHomes().associate { it.id to it.name }
        notify(overdue.map { names[it.homeId] ?: "Lar" })
        repo.saveReminded(reminded + overdue.associate { it.homeId to it.updatedAt })
        return Result.success()
    }

    private fun notify(homeNames: List<String>) {
        val context = applicationContext
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Lembretes de respostas", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Avisa quando um lar não responde há mais de uma semana."
            }
        )
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_REPLIES)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = homeNames.size
        val title = if (n == 1) "1 lar sem resposta" else "$n lares sem resposta"
        val text = "Há mais de uma semana — vale a pena telefonar."
        val style = NotificationCompat.InboxStyle().setSummaryText(text)
        homeNames.take(5).forEach { style.addLine(it) }
        if (n > 5) style.addLine("e mais ${n - 5}…")
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(style)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        if (canNotify(context)) NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "reply_reminders"
        private const val NOTIFICATION_ID = 1
        private const val WORK_NAME = "reply-reminders"

        fun canNotify(context: Context): Boolean =
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
                NotificationManagerCompat.from(context).areNotificationsEnabled()

        /** Schedules the daily check (idempotent). */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReplyReminderWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
