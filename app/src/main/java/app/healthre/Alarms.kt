package app.healthre

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.healthre.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZonedDateTime

object Alarms {
    private const val CH_DOSE = "dose"
    private const val CH_REFILL = "refill"
    private const val EXTRA_SID = "sid"
    private const val EXTRA_DAY = "day"
    private const val EXTRA_ACT = "act"

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_DOSE, "Dose reminders", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_REFILL, "Refill reminders", NotificationManager.IMPORTANCE_DEFAULT))
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

    private fun firePi(ctx: Context, sid: Long, flags: Int = PendingIntent.FLAG_UPDATE_CURRENT) = PendingIntent.getBroadcast(
        ctx, sid.toInt(), Intent(ctx, AlarmReceiver::class.java).putExtra(EXTRA_SID, sid), flags or PendingIntent.FLAG_IMMUTABLE,
    )

    fun canExact(ctx: Context) =
        Build.VERSION.SDK_INT < 31 || ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun schedule(ctx: Context, s: Schedule) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val at = nextTrigger(s)?.toInstant()?.toEpochMilli() ?: return
        val pi = firePi(ctx, s.id)
        if (canExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    fun cancel(ctx: Context, sid: Long) {
        firePi(ctx, sid, PendingIntent.FLAG_NO_CREATE)?.let { ctx.getSystemService(AlarmManager::class.java).cancel(it) }
    }

    suspend fun rescheduleAll(ctx: Context, dao: AppDao) = dao.allSchedules().forEach { schedule(ctx, it) }

    private fun notify(ctx: Context, id: Int, n: android.app.Notification) {
        if (NotificationManagerCompat.from(ctx).areNotificationsEnabled())
            try { NotificationManagerCompat.from(ctx).notify(id, n) } catch (_: SecurityException) {}
    }

    private fun actionPi(ctx: Context, sid: Long, day: Long, act: String, code: Int) = PendingIntent.getBroadcast(
        ctx, code, Intent(ctx, DoseActionReceiver::class.java).putExtra(EXTRA_SID, sid).putExtra(EXTRA_DAY, day).putExtra(EXTRA_ACT, act),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun notifyDose(ctx: Context, sm: ScheduleWithMedicine, day: Long) {
        val sid = sm.schedule.id
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CH_DOSE)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(sm.medicine.name)
            .setContentText("Time to take ${sm.medicine.unitDose} ${sm.medicine.strength}".trim())
            .setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open).setAutoCancel(true)
            .addAction(0, "Taken", actionPi(ctx, sid, day, "take", sid.toInt() * 2))
            .addAction(0, "Skip", actionPi(ctx, sid, day, "skip", sid.toInt() * 2 + 1))
            .build()
        notify(ctx, sid.toInt(), n)
    }

    fun notifyRefill(ctx: Context, m: Medicine) {
        val n = NotificationCompat.Builder(ctx, CH_REFILL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Refill ${m.name}")
            .setContentText("${m.stock} left (refill at ${m.refillThreshold})")
            .setAutoCancel(true).build()
        notify(ctx, 1_000_000 + m.id.toInt(), n)
    }

    suspend fun takeDose(ctx: Context, dao: AppDao, sid: Long, day: Long, quiet: Boolean = false) {
        val sm = dao.schedule(sid) ?: return
        if (dao.logOf(sid, day)?.status == DoseStatus.TAKEN) return
        dao.upsert(DoseLog(scheduleId = sid, epochDay = day, status = DoseStatus.TAKEN))
        dao.consume(sm.medicine.id, sm.medicine.unitDose.toIntOrNull() ?: 1)
        dao.medicine(sm.medicine.id)?.let { if (!quiet && it.refillThreshold > 0 && it.stock <= it.refillThreshold) notifyRefill(ctx, it) }
    }

    suspend fun skipDose(dao: AppDao, sid: Long, day: Long) {
        if (dao.logOf(sid, day) == null) dao.upsert(DoseLog(scheduleId = sid, epochDay = day, status = DoseStatus.SKIPPED))
    }

    suspend fun undoDose(dao: AppDao, sid: Long, day: Long) {
        val log = dao.logOf(sid, day) ?: return
        dao.clearLog(sid, day)
        if (log.status == DoseStatus.TAKEN) dao.schedule(sid)?.let { dao.restock(it.medicine.id, it.medicine.unitDose.toIntOrNull() ?: 1) }
    }

    fun receiverWork(ctx: Context, intent: BroadcastReceiver.PendingResult, block: suspend (HealthReApp) -> Unit) {
        val app = ctx.applicationContext as HealthReApp
        CoroutineScope(Dispatchers.IO).launch { try { block(app) } finally { intent.finish() } }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val sid = intent.getLongExtra("sid", -1)
        Alarms.receiverWork(ctx, goAsync()) { app ->
            val sm = app.db.dao().schedule(sid) ?: return@receiverWork
            Alarms.schedule(app, sm.schedule)
            val day = LocalDate.now().toEpochDay()
            if (app.db.dao().logOf(sid, day) == null) Alarms.notifyDose(app, sm, day)
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
            if (act == "take") Alarms.takeDose(app, dao, sid, day) else Alarms.skipDose(dao, sid, day)
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Alarms.receiverWork(ctx, goAsync()) { app -> Alarms.rescheduleAll(app, app.db.dao()) }
    }
}
