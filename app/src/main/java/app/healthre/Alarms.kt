package app.healthre

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.healthre.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

object Alarms {
    const val CH_STATUS = "status"
    private const val CH_DOSE = "dose_v2"
    private const val CH_ALARM = "dose_alarm"
    private const val CH_REFILL = "refill"
    private const val CH_APPT = "appointments"
    private const val APPT_BASE = 3_000_000
    private const val STATUS_ID = 2_000_000
    private const val TEST_ID = 2_000_001
    private const val MIDNIGHT_CODE = 2_000_002
    private const val EXTRA_SID = "sid"
    private const val EXTRA_DAY = "day"
    private const val EXTRA_ACT = "act"
    private const val EXTRA_REFRESH = "refresh"

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.deleteNotificationChannel("dose") // old channel: default sound, no vibration

        val notifSound = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        nm.createNotificationChannel(NotificationChannel(CH_DOSE, "Dose reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "A notification sound and vibration when a dose is due"
            setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI, notifSound)
            enableVibration(true); vibrationPattern = longArrayOf(0, 300, 150, 300)
        })

        val alarmSound = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        nm.createNotificationChannel(NotificationChannel(CH_ALARM, "Dose alarms (loud)", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Rings like an alarm, even on silent, until you respond"
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), alarmSound)
            enableVibration(true); vibrationPattern = longArrayOf(0, 600, 300, 600, 300, 600)
        })

        nm.createNotificationChannel(NotificationChannel(CH_REFILL, "Refill reminders", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_APPT, "Appointment reminders", NotificationManager.IMPORTANCE_HIGH))

        nm.createNotificationChannel(NotificationChannel(CH_STATUS, "Today's progress (persistent)", NotificationManager.IMPORTANCE_LOW).apply {
            description = "An always-visible summary of today's doses. Turn this off in your phone's notification settings to hide it."
            setShowBadge(false)
        })
    }

    fun nextTrigger(s: Schedule, now: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime? {
        for (i in 0..7L) {
            val d = now.toLocalDate().plusDays(i)
            if (s.daysMask and (1 shl (d.dayOfWeek.value - 1)) == 0) continue
            val t = d.atTime(s.minuteOfDay / 60, s.minuteOfDay % 60).atZone(now.zone)
            if (t.isAfter(now)) return t
        }
        return null
    }

    private fun firePi(ctx: Context, code: Int, extras: Intent.() -> Unit = {}, flags: Int = PendingIntent.FLAG_UPDATE_CURRENT) = PendingIntent.getBroadcast(
        ctx, code, Intent(ctx, AlarmReceiver::class.java).apply(extras), flags or PendingIntent.FLAG_IMMUTABLE,
    )

    /** True when the user let Doseful run in the background, so battery savers do not delay reminders. */
    fun backgroundAllowed(ctx: Context) =
        ctx.getSystemService(android.os.PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

    /** Opens the system prompt that asks to exempt Doseful from battery optimisation. */
    fun requestBackground(ctx: Context) {
        val pkg = android.net.Uri.parse("package:${ctx.packageName}")
        try {
            ctx.startActivity(Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg))
        } catch (e: Exception) {
            ctx.startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    /** Opens the system settings page for one notification channel (e.g. to hide the persistent one). */
    fun openChannelSettings(ctx: Context, channel: String) {
        ctx.startActivity(
            Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, channel),
        )
    }

    fun canExact(ctx: Context) =
        Build.VERSION.SDK_INT < 31 || ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun schedule(ctx: Context, s: Schedule) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val at = nextTrigger(s)?.toInstant()?.toEpochMilli() ?: return
        val pi = firePi(ctx, s.id.toInt(), { putExtra(EXTRA_SID, s.id) })
        if (canExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    fun cancel(ctx: Context, sid: Long) {
        firePi(ctx, sid.toInt(), { putExtra(EXTRA_SID, sid) }, PendingIntent.FLAG_NO_CREATE)?.let { ctx.getSystemService(AlarmManager::class.java).cancel(it) }
    }

    /** Wakes the app just after midnight so the persistent "today" notification rolls over to the new day. */
    private fun scheduleMidnight(ctx: Context) {
        val next = ZonedDateTime.now().toLocalDate().plusDays(1).atTime(0, 1).atZone(ZonedDateTime.now().zone)
        val pi = firePi(ctx, MIDNIGHT_CODE, { putExtra(EXTRA_REFRESH, true) })
        ctx.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), pi)
    }

    /** Reschedules every dose alarm, the midnight refresh, and refreshes the persistent notification. */
    suspend fun rescheduleAll(ctx: Context, dao: AppDao) {
        dao.allSchedules().forEach { schedule(ctx, it) }
        dao.appointmentsNow().forEach { scheduleAppointment(ctx, it) }
        scheduleMidnight(ctx)
        updateStatus(ctx, dao)
    }

    private fun notify(ctx: Context, id: Int, n: Notification) {
        if (NotificationManagerCompat.from(ctx).areNotificationsEnabled())
            try { NotificationManagerCompat.from(ctx).notify(id, n) } catch (_: SecurityException) {}
    }

    private fun actionPi(ctx: Context, sid: Long, day: Long, act: String, code: Int) = PendingIntent.getBroadcast(
        ctx, code, Intent(ctx, DoseActionReceiver::class.java).putExtra(EXTRA_SID, sid).putExtra(EXTRA_DAY, day).putExtra(EXTRA_ACT, act),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun openAppPi(ctx: Context) = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

    /** Builds a reminder in the chosen style. [loud] rings like an alarm and repeats until handled (or 5 minutes pass). */
    private fun reminder(ctx: Context, loud: Boolean, title: String, text: String) =
        NotificationCompat.Builder(ctx, if (loud) CH_ALARM else CH_DOSE)
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle(title).setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (loud) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openAppPi(ctx)).setAutoCancel(true)
            .apply { if (loud) setTimeoutAfter(5 * 60_000L) }

    private fun insistent(n: Notification, loud: Boolean) = n.also { if (loud) it.flags = it.flags or Notification.FLAG_INSISTENT }

    fun notifyDose(ctx: Context, sm: ScheduleWithMedicine, day: Long, loud: Boolean) {
        val sid = sm.schedule.id
        val m = sm.medicine
        val text = "Time to take ${m.unitDose} ${m.form.ifBlank { "dose" }.lowercase()}" + m.strength.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
        val n = reminder(ctx, loud, m.name, text)
            .addAction(0, "Taken", actionPi(ctx, sid, day, "take", sid.toInt() * 2))
            .addAction(0, "Skip", actionPi(ctx, sid, day, "skip", sid.toInt() * 2 + 1))
            .build()
        notify(ctx, sid.toInt(), insistent(n, loud))
    }

    /** Lets the user hear exactly what a reminder will sound like. */
    fun notifyTest(ctx: Context, loud: Boolean) {
        notify(ctx, TEST_ID, insistent(reminder(ctx, loud, "Test reminder", "This is how your dose reminders will sound.").build(), loud))
    }

    fun notifyRefill(ctx: Context, m: Medicine) {
        val n = NotificationCompat.Builder(ctx, CH_REFILL)
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle("Refill ${m.name}")
            .setContentText("${m.stock} left (refill at ${m.refillThreshold})")
            .setAutoCancel(true).build()
        notify(ctx, 1_000_000 + m.id.toInt(), n)
    }

    private fun apptPi(ctx: Context, id: Long, which: Int, flags: Int = PendingIntent.FLAG_UPDATE_CURRENT) = PendingIntent.getBroadcast(
        ctx, APPT_BASE + id.toInt() * 2 + which,
        Intent(ctx, AlarmReceiver::class.java).putExtra("appt", id).putExtra("which", which), flags or PendingIntent.FLAG_IMMUTABLE,
    )

    fun appointmentStart(a: Appointment): ZonedDateTime =
        LocalDate.ofEpochDay(a.epochDay).atTime(a.minuteOfDay / 60, a.minuteOfDay % 60).atZone(java.time.ZoneId.systemDefault())

    fun cancelAppointment(ctx: Context, id: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        for (which in 0..1) apptPi(ctx, id, which, PendingIntent.FLAG_NO_CREATE)?.let { am.cancel(it) }
    }

    /** Schedules the "day before" (0) and "hour before" (1) reminders that are switched on and still in the future. */
    fun scheduleAppointment(ctx: Context, a: Appointment) {
        cancelAppointment(ctx, a.id)
        val am = ctx.getSystemService(AlarmManager::class.java)
        val start = appointmentStart(a)
        val now = ZonedDateTime.now()
        listOf(Triple(0, a.remindDayBefore, start.minusDays(1)), Triple(1, a.remindHourBefore, start.minusHours(1))).forEach { (which, on, at) ->
            if (!on || !at.isAfter(now)) return@forEach
            val pi = apptPi(ctx, a.id, which)
            val ms = at.toInstant().toEpochMilli()
            if (canExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi) else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        }
    }

    fun notifyAppointment(ctx: Context, a: Appointment, which: Int) {
        val time = "%02d:%02d".format(a.minuteOfDay / 60, a.minuteOfDay % 60)
        val title = a.title + a.doctor.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
        val text = (if (which == 0) "Tomorrow at $time" else "In 1 hour, at $time") + a.place.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
        val n = NotificationCompat.Builder(ctx, CH_APPT)
            .setSmallIcon(R.drawable.ic_pill).setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_EVENT)
            .setContentIntent(openAppPi(ctx)).setAutoCancel(true).build()
        notify(ctx, 4_000_000 + a.id.toInt() * 2 + which, n)
    }

    /** The persistent notification: today's progress and the next dose. It uses its own low-priority channel so it can be hidden in system settings. */
    suspend fun updateStatus(ctx: Context, dao: AppDao) {
        val day = LocalDate.now()
        val bit = 1 shl (day.dayOfWeek.value - 1)
        val list = dao.schedulesNow().filter { it.schedule.daysMask and bit != 0 }
        val logs = dao.logsNow(day.toEpochDay()).associateBy { it.scheduleId }
        val done = list.count { logs[it.schedule.id]?.status == DoseStatus.TAKEN }
        val pending = list.filter { logs[it.schedule.id] == null }
        val nowMin = LocalTime.now().let { it.hour * 60 + it.minute }
        val next = pending.firstOrNull { it.schedule.minuteOfDay >= nowMin } ?: pending.firstOrNull()
        val text = when {
            list.isEmpty() -> "No doses scheduled today"
            pending.isEmpty() -> "All $done doses handled today"
            else -> "$done of ${list.size} taken" + next?.let { " · Next %02d:%02d %s".format(it.schedule.minuteOfDay / 60, it.schedule.minuteOfDay % 60, it.medicine.name) }.orEmpty()
        }
        val n = NotificationCompat.Builder(ctx, CH_STATUS)
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle("Doseful · Today").setContentText(text)
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true).setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS).setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppPi(ctx))
            .apply { if (list.isNotEmpty()) setProgress(list.size, done, false) }
            .build()
        notify(ctx, STATUS_ID, n)
    }

    /** [quiet] is true in demo mode: no refill notification and no change to the real persistent notification. */
    suspend fun takeDose(ctx: Context, dao: AppDao, sid: Long, day: Long, quiet: Boolean = false) {
        val sm = dao.schedule(sid) ?: return
        if (dao.logOf(sid, day)?.status == DoseStatus.TAKEN) return
        dao.upsert(DoseLog(scheduleId = sid, epochDay = day, status = DoseStatus.TAKEN))
        dao.consume(sm.medicine.id, sm.medicine.unitDose.toIntOrNull() ?: 1)
        if (quiet) return
        dao.medicine(sm.medicine.id)?.let { if (it.refillThreshold > 0 && it.stock <= it.refillThreshold) notifyRefill(ctx, it) }
        updateStatus(ctx, dao)
    }

    suspend fun skipDose(ctx: Context, dao: AppDao, sid: Long, day: Long, quiet: Boolean = false) {
        if (dao.logOf(sid, day) == null) dao.upsert(DoseLog(scheduleId = sid, epochDay = day, status = DoseStatus.SKIPPED))
        if (!quiet) updateStatus(ctx, dao)
    }

    suspend fun undoDose(ctx: Context, dao: AppDao, sid: Long, day: Long, quiet: Boolean = false) {
        val log = dao.logOf(sid, day) ?: return
        dao.clearLog(sid, day)
        if (log.status == DoseStatus.TAKEN) dao.schedule(sid)?.let { dao.restock(it.medicine.id, it.medicine.unitDose.toIntOrNull() ?: 1) }
        if (!quiet) updateStatus(ctx, dao)
    }

    fun receiverWork(ctx: Context, intent: BroadcastReceiver.PendingResult, block: suspend (HealthReApp) -> Unit) {
        val app = ctx.applicationContext as HealthReApp
        CoroutineScope(Dispatchers.IO).launch { try { block(app) } finally { intent.finish() } }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val sid = intent.getLongExtra("sid", -1)
        val refresh = intent.getBooleanExtra("refresh", false)
        val appt = intent.getLongExtra("appt", -1)
        val which = intent.getIntExtra("which", 1)
        Alarms.receiverWork(ctx, goAsync()) { app ->
            val dao = app.db.dao()
            if (appt > 0) {
                dao.appointment(appt)?.let { Alarms.notifyAppointment(app, it, which) }
                return@receiverWork
            }
            if (refresh) {
                Alarms.rescheduleAll(app, dao)
                return@receiverWork
            }
            val sm = dao.schedule(sid) ?: return@receiverWork
            Alarms.schedule(app, sm.schedule)
            val day = LocalDate.now().toEpochDay()
            if (dao.logOf(sid, day) == null) Alarms.notifyDose(app, sm, day, app.theme.reminderAlarm)
            Alarms.updateStatus(app, dao)
        }
    }
}

class DoseActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val sid = intent.getLongExtra("sid", -1)
        val day = intent.getLongExtra("day", -1)
        val act = intent.getStringExtra("act")
        NotificationManagerCompat.from(ctx).cancel(sid.toInt())
        Alarms.receiverWork(ctx, goAsync()) { app ->
            val dao = app.db.dao()
            if (act == "take") Alarms.takeDose(app, dao, sid, day) else Alarms.skipDose(app, dao, sid, day)
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Alarms.receiverWork(ctx, goAsync()) { app -> Alarms.rescheduleAll(app, app.db.dao()) }
    }
}
