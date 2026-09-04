package it.mavida.dashboardalert.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * Entity Room. I campi complessi (header, parametri delle condizioni,
 * configurazioni dei trigger) sono serializzati in JSON con
 * kotlinx.serialization: la struttura di questi campi evolve con le feature
 * e una colonna JSON evita migrazioni di schema ad ogni aggiunta.
 * I segreti NON passano mai di qui (vedi SecretsStore).
 */

@Entity(tableName = "monitors")
data class MonitorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val url: String,
    val method: String = "GET",
    /** JSON: List<HeaderEntry> (i valori segreti sono omessi, vedi SecretsStore). */
    val headersJson: String = "[]",
    val body: String? = null,
    val bodyContentType: String? = null,
    val intervalSeconds: Int = 60,
    val timeoutSeconds: Int = 15,
    val maxRetries: Int = 2,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "rules",
    foreignKeys = [
        ForeignKey(
            entity = MonitorEntity::class,
            parentColumns = ["id"],
            childColumns = ["monitorId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("monitorId")],
)
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val monitorId: Long,
    val name: String = "",
    /** JSON di ConditionParams (include il discriminatore "type"). */
    val conditionJson: String,
    val enabled: Boolean = true,
)

@Entity(
    tableName = "triggers",
    foreignKeys = [
        ForeignKey(
            entity = RuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["ruleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("ruleId")],
)
data class TriggerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ruleId: Long,
    /** JSON di TriggerConfig (include il discriminatore "type"). */
    val configJson: String,
    val enabled: Boolean = true,
    val cooldownSeconds: Int = 300,
    val repeatWhileTrue: Boolean = false,
    val repeatIntervalSeconds: Int = 60,
)

/** Storico degli eventi (spec §3.6): risposte, esiti regole, trigger scattati. */
@Entity(
    tableName = "log_entries",
    indices = [Index("timestamp"), Index("monitorId")],
)
data class LogEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    /** Null per eventi non legati a un monitor specifico (es. avvio service). */
    val monitorId: Long? = null,
    val monitorName: String = "",
    val level: String = "INFO",
    val message: String,
    /** Dettagli opzionali (es. snippet del body di risposta). */
    val detail: String? = null,
)

// --- Proiezioni con relazioni per caricare un monitor completo in una query ---

data class RuleWithTriggers(
    @Embedded val rule: RuleEntity,
    @Relation(parentColumn = "id", entityColumn = "ruleId")
    val triggers: List<TriggerEntity>,
)

data class MonitorWithRelations(
    @Embedded val monitor: MonitorEntity,
    @Relation(entity = RuleEntity::class, parentColumn = "id", entityColumn = "monitorId")
    val rules: List<RuleWithTriggers>,
)
