package app.healthre.data

import app.healthre.Alarms
import app.healthre.AppEnv
import org.json.JSONArray
import org.json.JSONObject

object Backup {
    private const val VERSION = 1

    suspend fun export(app: AppEnv): String {
        val d = app.db.dao()
        fun <T> arr(l: List<T>, f: (T) -> JSONObject) = JSONArray().also { a -> l.forEach { a.put(f(it)) } }
        return JSONObject()
            .put("version", VERSION)
            .put("profile", app.profile.value.toJson())
            .put("avatar", app.profile.avatarBase64() ?: "")
            .put("medicalId", app.profile.medical.toJson())
            .put("appointments", arr(d.appointmentsNow()) {
                JSONObject().put("id", it.id).put("title", it.title).put("doctor", it.doctor).put("place", it.place).put("phone", it.phone)
                    .put("epochDay", it.epochDay).put("minuteOfDay", it.minuteOfDay).put("notes", it.notes)
                    .put("remindDayBefore", it.remindDayBefore).put("remindHourBefore", it.remindHourBefore)
            })
            .put("measurements", arr(d.measurementsNow()) {
                JSONObject().put("type", it.type).put("value", it.value.toDouble()).put("value2", it.value2.toDouble())
                    .put("recordedAt", it.recordedAt).put("note", it.note)
            })
            .put("medicines", arr(d.allMedicines()) {
                JSONObject().put("id", it.id).put("name", it.name).put("strength", it.strength).put("form", it.form)
                    .put("unitDose", it.unitDose).put("stock", it.stock).put("refillThreshold", it.refillThreshold)
                    .put("manufacturer", it.manufacturer).put("expiry", it.expiry).put("batch", it.batch).put("notes", it.notes)
            })
            .put("schedules", arr(d.allSchedules()) {
                JSONObject().put("id", it.id).put("medicineId", it.medicineId).put("minuteOfDay", it.minuteOfDay).put("daysMask", it.daysMask)
            })
            .put("logs", arr(d.allLogs()) {
                JSONObject().put("scheduleId", it.scheduleId).put("epochDay", it.epochDay).put("status", it.status.name).put("loggedAt", it.loggedAt)
            })
            .toString(2)
    }

    /** Replaces ALL data. Parses fully before touching the DB so a bad file changes nothing. */
    suspend fun import(app: AppEnv, json: String) {
        val j = JSONObject(json)
        require(j.getInt("version") == VERSION) { "Unsupported backup version" }
        fun <T> list(key: String, f: (JSONObject) -> T) = j.getJSONArray(key).let { a -> List(a.length()) { f(a.getJSONObject(it)) } }
        val meds = list("medicines") {
            Medicine(it.getLong("id"), it.getString("name"), it.optString("strength"), it.optString("form"), it.optString("unitDose", "1"),
                it.optInt("stock"), it.optInt("refillThreshold"), it.optString("manufacturer"), it.optString("expiry"), it.optString("batch"), it.optString("notes"))
        }
        val scheds = list("schedules") { Schedule(it.getLong("id"), it.getLong("medicineId"), it.getInt("minuteOfDay"), it.getInt("daysMask")) }
        val logs = list("logs") { DoseLog(0, it.getLong("scheduleId"), it.getLong("epochDay"), DoseStatus.valueOf(it.getString("status")), it.optLong("loggedAt")) }
        val profile = Profile.fromJson(j.getJSONObject("profile"))
        val avatar = j.optString("avatar")
        val medical = j.optJSONObject("medicalId")?.let { MedicalId.fromJson(it) } ?: MedicalId()
        fun <T> optList(key: String, f: (JSONObject) -> T) = j.optJSONArray(key)?.let { a -> List(a.length()) { f(a.getJSONObject(it)) } } ?: emptyList()
        val appts = optList("appointments") {
            Appointment(it.getLong("id"), it.getString("title"), it.optString("doctor"), it.optString("place"), it.optString("phone"),
                it.getLong("epochDay"), it.getInt("minuteOfDay"), it.optString("notes"), it.optBoolean("remindDayBefore", true), it.optBoolean("remindHourBefore", true))
        }
        val measures = optList("measurements") {
            Measurement(0, it.getString("type"), it.getDouble("value").toFloat(), it.optDouble("value2", 0.0).toFloat(), it.optLong("recordedAt"), it.optString("note"))
        }

        val d = app.db.dao()
        if (!app.demo) {
            d.allSchedules().forEach { Alarms.cancel(app.ctx, it.id) }
            d.appointmentsNow().forEach { Alarms.cancelAppointment(app.ctx, it.id) }
        }
        app.db.clearAllTables()
        d.insertMedicines(meds); d.insertSchedules(scheds); d.insertLogs(logs)
        d.insertAppointments(appts); d.insertMeasurements(measures)
        app.profile.saveMedical(medical)
        app.profile.save(profile.copy(onboarded = true))
        app.profile.setAvatarBase64(avatar)
        if (!app.demo) Alarms.rescheduleAll(app.ctx, d)
    }
}
