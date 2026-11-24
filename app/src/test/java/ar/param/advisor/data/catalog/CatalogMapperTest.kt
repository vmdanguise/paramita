package ar.param.advisor.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CatalogMapperTest {

    private fun practice(id: String, type: String = "PRAYER", phase: String = "CORE") = PracticeDto(
        id = id,
        type = type,
        title = "Título $id",
        segments = listOf(SegmentDto(order = 1, phase = phase, heading = "h", text = "  texto  ", repeatCount = 0, pauseAfterSec = -3)),
        mantraConfig = if (type == "MANTRA") MantraConfigDto() else null,
        meritDedication = "Que beneficie a todos los seres.",
        tags = listOf("Refugio", "refugio"),
        jewelFocus = listOf("darma", "XYZ"),
    )

    @Test
    fun ac004_unknownTypeIsRejectedAndTheRestAreKept() {
        val bundle = CatalogBundleDto(3, practices = listOf(practice("prac_ok"), practice("prac_foo", type = "FOO")))
        val mapped = CatalogMapper.map(bundle, emptySet(), 0L)
        assertEquals(listOf("prac_ok"), mapped.practices.map { it.id })
        assertEquals("prac_foo", mapped.rejected.single().practiceId)
    }

    @Test
    fun unknownPhaseIsRejected() {
        val mapped = CatalogMapper.map(CatalogBundleDto(3, practices = listOf(practice("prac_x", phase = "RARO"))), emptySet(), 0L)
        assertTrue(mapped.practices.isEmpty())
    }

    @Test
    fun aliasesAndNormalization() {
        assertEquals("PRAYER", CatalogMapper.normalizeType("oración"))
        assertEquals("GUIDED_MEDITATION", CatalogMapper.normalizeType("MEDITACION"))
        assertEquals("POSTURE_BREATH", CatalogMapper.normalizePhase("breath"))
        assertNull(CatalogMapper.normalizeType("FOO"))

        val mapped = CatalogMapper.map(CatalogBundleDto(3, practices = listOf(practice("prac_a"))), setOf("prac_a"), 42L)
        val seg = mapped.segments.single()
        assertEquals("prac_a_seg_1", seg.id)
        assertEquals("texto", seg.text)
        assertEquals(1, seg.repeatCount)
        assertEquals(0, seg.pauseAfterSec)
        assertEquals(listOf("refugio"), mapped.tags.map { it.tag })
        assertEquals(listOf("DHARMA"), mapped.jewels.map { it.jewel })
        assertTrue("conserva el favorito", mapped.practices.single().isFavorite)
    }

    @Test
    fun mantraWithoutConfigIsRejected() {
        val dto = practice("prac_m", type = "MANTRA").copy(mantraConfig = null)
        assertEquals("mantra sin mantra_config", CatalogMapper.validate(dto, emptySet()))
    }

    @Test
    fun ac006_repoCatalogPassesAllValidations() {
        val raw = File("src/main/assets/catalog_v1.json").readText()
        val bundle = CatalogMapper.parse(raw)
        val mapped = CatalogMapper.map(bundle, emptySet(), 0L)
        assertTrue("rechazadas: ${mapped.rejected}", mapped.rejected.isEmpty())
        assertEquals(8, mapped.practices.size)
        assertEquals(1, mapped.mantraConfigs.size)
        assertTrue(mapped.practices.any { it.id == "prac_refugio_tres_joyas" })
    }

    @Test
    fun catalogUsesVoseoInGuideTexts() {
        val raw = File("src/main/assets/catalog_v1.json").readText()
        listOf("Siéntate", "Suelta los", "Detente", "Deja que", "No necesitas").forEach {
            assertFalse("quedó tuteo: $it", raw.contains(it))
        }
    }
}
