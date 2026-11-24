package ar.param.advisor.data.catalog

import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import ar.param.advisor.data.local.dao.PracticeDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Siembra el catálogo de assets en Room (spec-data-catalogo-json, REQ-002..REQ-009).
 * Nunca corre en el hilo principal y nunca toca la DB si el JSON está mal formado.
 */
class CatalogSeeder(
    private val readCatalog: () -> String,
    private val practiceDao: PracticeDao,
    private val prefs: SharedPreferences,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun seedIfNeeded(forceReload: Boolean = false): Result<Int> = withContext(Dispatchers.IO) {
        try {
            Result.success(seedFrom(readCatalog(), forceReload))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo sembrar el catálogo; se mantiene el anterior", e)
            Result.failure(e)
        }
    }

    private suspend fun seedFrom(raw: String, forceReload: Boolean): Int {
        val bundle = CatalogMapper.parse(raw)
        val seededVersion = prefs.getInt(KEY_SEEDED_SCHEMA_VERSION, 0)
        val isEmpty = practiceDao.countPractices() == 0
        if (!forceReload && !isEmpty && bundle.schemaVersion <= seededVersion) return 0

        val favorites = practiceDao.getFavoriteIds().toSet()
        val mapped = CatalogMapper.map(bundle, favorites, clock())
        mapped.rejected.forEach { Log.w(TAG, "Práctica rechazada '${it.practiceId}': ${it.reason}") }
        check(mapped.practices.isNotEmpty()) { "El catálogo no tiene prácticas válidas" }

        practiceDao.replaceCatalogBundle(
            practices = mapped.practices,
            segments = mapped.segments,
            mantraConfigs = mapped.mantraConfigs,
            tags = mapped.tags,
            jewels = mapped.jewels,
        )
        prefs.edit(commit = true) { putInt(KEY_SEEDED_SCHEMA_VERSION, bundle.schemaVersion) }
        return mapped.practices.size
    }

    companion object {
        const val PREFS_NAME = "paramita_catalog_prefs"
        const val KEY_SEEDED_SCHEMA_VERSION = "seeded_schema_version"
        const val ASSET_NAME = "catalog_v1.json"
        private const val TAG = "CatalogSeeder"
    }
}
