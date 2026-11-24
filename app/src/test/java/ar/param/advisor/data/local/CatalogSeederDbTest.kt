package ar.param.advisor.data.local

import android.content.Context
import androidx.room.Room
import ar.param.advisor.data.catalog.CatalogSeeder
import ar.param.advisor.data.local.entity.PracticeSessionEntity
import ar.param.advisor.engine.toPracticeRunSpec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

// SDK 35: el sandbox de Robolectric para SDK 36 exige Java 21 y el proyecto compila con Java 17.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CatalogSeederDbTest {

    private lateinit var db: ManiDatabase
    private lateinit var context: Context
    private val realCatalog = File("src/main/assets/catalog_v1.json").readText()

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, ManiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    private fun seeder(raw: () -> String) = CatalogSeeder(
        readCatalog = raw,
        practiceDao = db.practiceDao(),
        prefs = context.getSharedPreferences("test_prefs", Context.MODE_PRIVATE),
    )

    private fun bump(raw: String, version: Int) = raw.replace(Regex("\"schema_version\": \\d+"), "\"schema_version\": $version")

    @Test
    fun ac001_ac002_firstRunSeeds8AndSecondRunDoesNothing() = runBlocking {
        val s = seeder { realCatalog }
        assertEquals(8, s.seedIfNeeded().getOrThrow())
        assertEquals(8, db.practiceDao().countPractices())
        assertEquals(0, s.seedIfNeeded().getOrThrow())
    }

    @Test
    fun ac004_mantraLoadsWithConfig() = runBlocking {
        seeder { realCatalog }.seedIfNeeded().getOrThrow()
        val mantra = db.practiceDao().getPracticeByIdOnce("prac_mantra_corazon")
        assertNotNull(mantra)
        assertEquals(1, mantra!!.segments.size)
        assertEquals(21, mantra.mantraConfig?.targetRepetitions)
        assertEquals(listOf(3, 7, 21, 108), mantra.toPracticeRunSpec().mantraConfig?.presetCounts)
    }

    @Test
    fun ac001_favoritesAndSessionsSurviveCatalogUpdate() = runBlocking {
        val prefs = context.getSharedPreferences("test_prefs", Context.MODE_PRIVATE)
        seeder { realCatalog }.seedIfNeeded().getOrThrow()
        db.practiceDao().setFavorite("prac_refugio_tres_joyas", true)
        repeat(3) {
            db.practiceSessionDao().insertSession(
                PracticeSessionEntity("s$it", "prac_refugio_tres_joyas", 1_000L * it, null, 0, "GUIDED", true, false, null),
            )
        }
        val v = prefs.getInt(CatalogSeeder.KEY_SEEDED_SCHEMA_VERSION, 0)
        assertEquals(8, seeder { bump(realCatalog, v + 1) }.seedIfNeeded().getOrThrow())
        assertEquals(listOf("prac_refugio_tres_joyas"), db.practiceDao().getFavoriteIds())
        assertEquals(3, db.practiceSessionDao().observeSessionsCountSince(0L).first())
    }

    @Test
    fun ac002_removedPracticeIsArchivedAndHistoryKept() = runBlocking {
        seeder { realCatalog }.seedIfNeeded().getOrThrow()
        db.practiceSessionDao().insertSession(
            PracticeSessionEntity("s1", "prac_calma_digital_noche", 1L, null, 0, "GUIDED", true, false, null),
        )
        val withoutCalma = bump(realCatalog, 99).replace("\"id\": \"prac_calma_digital_noche\"", "\"id\": \"prac_calma_digital_noche_v2\"")
        seeder { withoutCalma }.seedIfNeeded().getOrThrow()
        val visible = db.practiceDao().observeAllPracticesWithDetails().first().map { it.practice.id }
        assertTrue("prac_calma_digital_noche" !in visible)
        assertNull(db.practiceDao().getPracticeByIdOnce("prac_calma_digital_noche"))
        assertEquals(1, db.practiceSessionDao().observeSessionsCountSince(0L).first())
    }

    @Test
    fun ac005_corruptJsonFailsAndKeepsDb() = runBlocking {
        seeder { realCatalog }.seedIfNeeded().getOrThrow()
        val result = seeder { "{ esto no es json" }.seedIfNeeded(forceReload = true)
        assertTrue(result.isFailure)
        assertEquals(8, db.practiceDao().countPractices())
    }

    @Test
    fun ac005_orderedSegmentsAreSorted() = runBlocking {
        seeder { realCatalog }.seedIfNeeded().getOrThrow()
        val maitri = db.practiceDao().getPracticeByIdOnce("prac_med_amor_bondadoso")!!
        assertEquals(listOf(1, 2, 3), maitri.orderedSegments.map { it.segmentOrder })
    }
}
