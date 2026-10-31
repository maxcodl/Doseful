package app.healthre.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity
data class Medicine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val strength: String = "",
    val form: String = "",
    val unitDose: String = "1",
    val stock: Int = 0,
    val refillThreshold: Int = 0,
    val manufacturer: String = "",
    val expiry: String = "",
    val batch: String = "",
    val notes: String = "",
)

/** One row per daily time. [daysMask]: bit0=Mon..bit6=Sun. */
@Entity(
    foreignKeys = [ForeignKey(Medicine::class, ["id"], ["medicineId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("medicineId")],
)
data class Schedule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicineId: Long,
    val minuteOfDay: Int,
    val daysMask: Int = 0b1111111,
)

enum class DoseStatus { TAKEN, SKIPPED }

@Entity(
    foreignKeys = [ForeignKey(Schedule::class, ["id"], ["scheduleId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["scheduleId", "epochDay"], unique = true)],
)
data class DoseLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scheduleId: Long,
    val epochDay: Long,
    val status: DoseStatus,
    val loggedAt: Long = System.currentTimeMillis(),
)

data class ScheduleWithMedicine(
    @Embedded val schedule: Schedule,
    @Relation(parentColumn = "medicineId", entityColumn = "id") val medicine: Medicine,
)

@Dao
interface AppDao {
    @Query("SELECT * FROM Medicine ORDER BY name") fun medicines(): Flow<List<Medicine>>
    @Query("SELECT * FROM Medicine WHERE id = :id") suspend fun medicine(id: Long): Medicine?
    @Insert suspend fun insert(m: Medicine): Long
    @Update suspend fun update(m: Medicine)
    @Delete suspend fun delete(m: Medicine)

    @Insert suspend fun insert(s: Schedule): Long
    @Update suspend fun update(s: Schedule)
    @Delete suspend fun delete(s: Schedule)
    @Query("SELECT * FROM Schedule WHERE medicineId = :id") suspend fun schedulesOf(id: Long): List<Schedule>
    @Transaction @Query("SELECT * FROM Schedule WHERE id = :id") suspend fun schedule(id: Long): ScheduleWithMedicine?
    @Transaction @Query("SELECT * FROM Schedule ORDER BY minuteOfDay")
    fun schedules(): Flow<List<ScheduleWithMedicine>>

    @Transaction @Query("SELECT * FROM Schedule ORDER BY minuteOfDay") suspend fun schedulesNow(): List<ScheduleWithMedicine>
    @Query("SELECT * FROM DoseLog WHERE epochDay = :day") suspend fun logsNow(day: Long): List<DoseLog>
    @Query("SELECT * FROM DoseLog WHERE epochDay = :day") fun logs(day: Long): Flow<List<DoseLog>>
    @Query("SELECT * FROM DoseLog WHERE epochDay BETWEEN :from AND :to")
    fun logsBetween(from: Long, to: Long): Flow<List<DoseLog>>
    @Query("SELECT * FROM DoseLog WHERE scheduleId = :sid AND epochDay = :day")
    suspend fun logOf(sid: Long, day: Long): DoseLog?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(l: DoseLog)
    @Query("DELETE FROM DoseLog WHERE scheduleId = :sid AND epochDay = :day")
    suspend fun clearLog(sid: Long, day: Long)

    @Query("UPDATE Medicine SET stock = MAX(stock - :n, 0) WHERE id = :id") suspend fun consume(id: Long, n: Int)
    @Query("UPDATE Medicine SET stock = stock + :n WHERE id = :id") suspend fun restock(id: Long, n: Int)

    // Backup / restore
    @Query("SELECT * FROM Medicine") suspend fun allMedicines(): List<Medicine>
    @Query("SELECT * FROM Schedule") suspend fun allSchedules(): List<Schedule>
    @Query("SELECT * FROM DoseLog") suspend fun allLogs(): List<DoseLog>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertMedicines(l: List<Medicine>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSchedules(l: List<Schedule>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertLogs(l: List<DoseLog>)
}

class Converters {
    @TypeConverter fun toStatus(s: String) = DoseStatus.valueOf(s)
    @TypeConverter fun fromStatus(s: DoseStatus) = s.name
}

@Database(entities = [Medicine::class, Schedule::class, DoseLog::class], version = 2)
@TypeConverters(Converters::class)
abstract class Db : RoomDatabase() {
    abstract fun dao(): AppDao
}
