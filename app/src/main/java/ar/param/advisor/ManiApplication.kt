package ar.param.advisor

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import ar.param.advisor.data.catalog.CatalogSeeder
import ar.param.advisor.data.local.ManiDatabase
import ar.param.advisor.data.settings.ManiSettings
import ar.param.advisor.data.user.UserMediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

/** Inyección manual (ADR-0002): la DB, los DAOs y la siembra viven acá. */
class ManiApplication : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: ManiDatabase by lazy {
        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        ManiDatabase.build(this, debuggable)
    }
    val practiceDao by lazy { database.practiceDao() }
    val practiceSessionDao by lazy { database.practiceSessionDao() }
    val meritAndCalmDao by lazy { database.meritAndCalmDao() }
    val mindfulAppTargetDao by lazy { database.mindfulAppTargetDao() }
    val settings: ManiSettings by lazy { ManiSettings(this) }
    val userMedia: UserMediaStore by lazy { UserMediaStore(this) }

    lateinit var catalogSeed: Deferred<Result<Int>>
        private set

    override fun onCreate() {
        super.onCreate()
        val seeder = CatalogSeeder(
            readCatalog = { assets.open(CatalogSeeder.ASSET_NAME).bufferedReader().use { it.readText() } },
            practiceDao = practiceDao,
            prefs = getSharedPreferences(CatalogSeeder.PREFS_NAME, Context.MODE_PRIVATE),
        )
        catalogSeed = applicationScope.async { seeder.seedIfNeeded() }
    }
}
