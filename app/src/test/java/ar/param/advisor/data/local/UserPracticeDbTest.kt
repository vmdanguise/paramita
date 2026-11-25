package ar.param.advisor.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import ar.param.advisor.data.catalog.CatalogSeeder
import ar.param.advisor.data.local.entity.PracticeEntity
import ar.param.advisor.data.local.entity.PracticeSessionEntity
import ar.param.advisor.data.local.entity.PracticeTypes
import ar.param.advisor.data.user.UserPracticeForm
import ar.param.advisor.engine.toPracticeRunSpec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

/** Prácticas propias (ADR-0008) y migración de la base 1 → 2 (ADR-0010). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UserPracticeDbTest {

    private lateinit var db: ManiDatabase
    private lateinit var context: Context
    private val realCatalog = File("src/main/assets/catalog_v1.json").readText()
    private val dedication = "Que el mérito beneficie a todos los seres."

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, ManiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun save(form: UserPracticeForm) {
        val b = form.toEntities(dedication, "Práctica propia")
        db.practiceDao().saveUserPractice(b.practice, b.segments, b.mantraConfig)
    }

    @Test
    fun prayerIsSavedWithOneCoreSegmentAndRuns() = runBlocking {
        val form = UserPracticeForm(title = "Mi oración", text = "Que todos los seres estén bien.", repetitions = 3, pauseSec = 4)
        save(form)
        val details = db.practiceDao().getPracticeByIdOnce(form.id)!!
        assertEquals(PracticeTypes.PRAYER, details.practice.type)
        assertTrue(details.practice.isUserCreated)
        assertEquals(dedication, details.practice.meritDedication)
        assertNull(details.mantraConfig)
        val seg = details.orderedSegments.single()
        assertEquals("CORE", seg.phase)
        assertEquals(3, seg.repeatCount)
        assertEquals(4, seg.pauseAfterSec)
        val spec = details.toPracticeRunSpec()
        assertEquals(1, spec.segments.size)
        assertNull(spec.segments.single().audioPath)
    }

    @Test
    fun mantraWithRecordingAndImageRoundTrips() = runBlocking {
        val form = UserPracticeForm(
            type = PracticeTypes.MANTRA,
            title = "Om mani",
            text = "Om mani padme hum",
            repetitions = 108,
            pauseSec = 1,
            bellEveryN = 27,
            paceWpm = 150,
            recordingPath = "/data/user/0/ar.param.advisor/files/practice_recordings/rec_1.m4a",
            imagePath = "/data/user/0/ar.param.advisor/files/practice_images/img_1.jpg",
        )
        save(form)
        val details = db.practiceDao().getPracticeByIdOnce(form.id)!!
        val mantra = details.mantraConfig!!
        assertEquals(108, mantra.targetRepetitions)
        assertEquals(27, mantra.bellEveryN)
        assertEquals(1_000L, mantra.pauseBetweenRepsMs)
        assertEquals(150, details.practice.defaultPaceWpm)
        val spec = details.toPracticeRunSpec()
        assertEquals(form.recordingPath, spec.segments.single().audioPath)
        assertEquals(form.imagePath, spec.imagePath)
        // Al volver a abrirla en el editor, queda igual.
        assertEquals(form, UserPracticeForm.from(details, dedication))
    }

    @Test
    fun editingReplacesSegmentsAndKeepsFavorite() = runBlocking {
        val form = UserPracticeForm(type = PracticeTypes.MANTRA, title = "Mantra", text = "Om", repetitions = 21)
        save(form)
        db.practiceDao().setFavorite(form.id, true)
        save(form.copy(type = PracticeTypes.PRAYER, text = "Oración nueva", repetitions = 2))
        val details = db.practiceDao().getPracticeByIdOnce(form.id)!!
        assertNull(details.mantraConfig)
        assertEquals("Oración nueva", details.orderedSegments.single().text)
        assertTrue(details.practice.isFavorite)
    }

    @Test
    fun deletingArchivesAndKeepsHistory() = runBlocking {
        val form = UserPracticeForm(title = "Para borrar", text = "Texto")
        save(form)
        db.practiceSessionDao().insertSession(
            PracticeSessionEntity(
                id = "s1",
                practiceId = form.id,
                startedAtEpochMs = 1L,
                endedAtEpochMs = 2L,
                completedRepetitions = 1,
                recitationMode = "SPOKEN",
                completedFull = true,
                meritDedicated = true,
                note = null,
            ),
        )
        db.practiceDao().archiveUserPractice(form.id)
        assertNull(db.practiceDao().getPracticeByIdOnce(form.id))
        assertEquals("s1", db.practiceSessionDao().getLatestSessionForPractice(form.id)?.id)
    }

    @Test
    fun catalogPracticesCannotBeArchivedAsUserPractices() = runBlocking {
        val seeder = CatalogSeeder({ realCatalog }, db.practiceDao(), context.getSharedPreferences("t", Context.MODE_PRIVATE))
        seeder.seedIfNeeded().getOrThrow()
        db.practiceDao().archiveUserPractice("prac_refugio_tres_joyas")
        assertNotNull(db.practiceDao().getPracticeByIdOnce("prac_refugio_tres_joyas"))
    }

    @Test
    fun catalogUpdateDoesNotArchiveUserPractices() = runBlocking {
        val prefs = context.getSharedPreferences("t2", Context.MODE_PRIVATE)
        CatalogSeeder({ realCatalog }, db.practiceDao(), prefs).seedIfNeeded().getOrThrow()
        val form = UserPracticeForm(title = "Mía", text = "Texto propio")
        save(form)
        val bumped = realCatalog.replace(Regex("\"schema_version\": \\d+"), "\"schema_version\": 99")
        CatalogSeeder({ bumped }, db.practiceDao(), prefs).seedIfNeeded().getOrThrow()
        assertNotNull(db.practiceDao().getPracticeByIdOnce(form.id))
        assertEquals(9, db.practiceDao().observeAllPracticesWithDetails().first().size)
    }

    @Test
    fun migration1To2KeepsDataAndAddsImageColumn() {
        db.close()
        val name = "migracion_test.db"
        context.deleteDatabase(name)
        val schema = Json.parseToJsonElement(File("schemas/ar.param.advisor.data.local.ManiDatabase/1.json").readText())
            .jsonObject.getValue("database").jsonObject
        val legacy = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile?.mkdirs() }, null)
        schema.getValue("entities").jsonArray.forEach { entity ->
            val e = entity.jsonObject
            val table = e.getValue("tableName").jsonPrimitive.content
            legacy.execSQL(e.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            e["indices"]?.jsonArray?.forEach { idx ->
                legacy.execSQL(idx.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            }
        }
        schema.getValue("setupQueries").jsonArray.forEach { legacy.execSQL(it.jsonPrimitive.content) }
        legacy.execSQL(
            "INSERT INTO practices (id, type, title, subtitle, tradition, estimated_minutes, audio_asset_uri, tts_enabled, " +
                "default_pace_wpm, merit_dedication, paramita_section, paramita_topic, paramita_url_path, is_favorite, is_archived, " +
                "updated_at_epoch_ms) VALUES ('prac_refugio_tres_joyas', 'PRAYER', 'Refugio', NULL, 'Tradición', 3, NULL, 1, 120, " +
                "'Dedicación', NULL, NULL, NULL, 1, 0, 1)",
        )
        legacy.version = 1
        legacy.close()

        val migrated = Room.databaseBuilder(context, ManiDatabase::class.java, name).allowMainThreadQueries().build()
        try {
            runBlocking {
                val p: PracticeEntity = migrated.practiceDao().getPracticeByIdOnce("prac_refugio_tres_joyas")!!.practice
                assertTrue(p.isFavorite)
                assertNull(p.imageUri)
                assertFalse(p.isUserCreated)
            }
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
        db = Room.inMemoryDatabaseBuilder(context, ManiDatabase::class.java).allowMainThreadQueries().build()
    }
}
