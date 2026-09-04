package it.mavida.dashboardalert.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MonitorDao {

    /** Flow reattivo: la UI e il PollingService si aggiornano da soli a ogni modifica. */
    @Transaction
    @Query("SELECT * FROM monitors ORDER BY name")
    fun observeAllWithRelations(): Flow<List<MonitorWithRelations>>

    @Transaction
    @Query("SELECT * FROM monitors WHERE id = :id")
    suspend fun getWithRelations(id: Long): MonitorWithRelations?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMonitor(monitor: MonitorEntity): Long

    @Update
    suspend fun updateMonitor(monitor: MonitorEntity)

    @Delete
    suspend fun deleteMonitor(monitor: MonitorEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRules(rules: List<RuleEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTriggers(triggers: List<TriggerEntity>)

    /** Rimozione selettiva: il repository cancella regole/trigger non piu' presenti nel draft. */
    @Query("DELETE FROM rules WHERE monitorId = :monitorId AND id NOT IN (:keepIds)")
    suspend fun deleteRulesNotIn(monitorId: Long, keepIds: List<Long>)

    @Query("DELETE FROM rules WHERE monitorId = :monitorId")
    suspend fun deleteAllRulesOf(monitorId: Long)

    @Query("DELETE FROM triggers WHERE ruleId IN (:ruleIds) AND id NOT IN (:keepIds)")
    suspend fun deleteTriggersNotIn(ruleIds: List<Long>, keepIds: List<Long>)

    @Query("DELETE FROM triggers WHERE ruleId IN (:ruleIds)")
    suspend fun deleteAllTriggersOfRules(ruleIds: List<Long>)
}

@Dao
interface LogDao {

    @Query("SELECT * FROM log_entries ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 500): Flow<List<LogEntryEntity>>

    @Insert
    suspend fun insert(entry: LogEntryEntity): Long

    @Query("DELETE FROM log_entries")
    suspend fun clear()

    /** Rotazione dello storico: mantiene solo le voci piu' recenti. */
    @Query(
        "DELETE FROM log_entries WHERE id NOT IN " +
            "(SELECT id FROM log_entries ORDER BY timestamp DESC LIMIT :keep)",
    )
    suspend fun trimTo(keep: Int)
}
