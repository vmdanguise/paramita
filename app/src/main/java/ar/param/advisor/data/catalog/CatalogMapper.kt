package ar.param.advisor.data.catalog

import ar.param.advisor.data.local.entity.Jewels
import ar.param.advisor.data.local.entity.MantraConfigEntity
import ar.param.advisor.data.local.entity.PracticeEntity
import ar.param.advisor.data.local.entity.PracticeJewelFocusEntity
import ar.param.advisor.data.local.entity.PracticeSegmentEntity
import ar.param.advisor.data.local.entity.PracticeTagEntity
import ar.param.advisor.data.local.entity.PracticeTypes
import ar.param.advisor.data.local.entity.SegmentPhases
import kotlinx.serialization.json.Json

/**
 * DTOs del catálogo → entidades Room. Los textos se copian literalmente (solo trim, CON-001):
 * el mapper nunca reescribe contenido litúrgico.
 */
object CatalogMapper {

    val json = Json { ignoreUnknownKeys = true }

    private const val DEFAULT_TRADITION = "Budismo Tibetano · Sakya (Paramita)"
    private val ID_PATTERN = Regex("^prac_[a-z0-9_]+$")

    data class Rejection(val practiceId: String, val reason: String)

    data class MappedCatalog(
        val practices: List<PracticeEntity>,
        val segments: List<PracticeSegmentEntity>,
        val mantraConfigs: List<MantraConfigEntity>,
        val tags: List<PracticeTagEntity>,
        val jewels: List<PracticeJewelFocusEntity>,
        val rejected: List<Rejection>,
    )

    fun parse(raw: String): CatalogBundleDto = json.decodeFromString(CatalogBundleDto.serializer(), raw)

    fun normalizeType(raw: String): String? = when (val v = raw.trim().uppercase()) {
        "ORACION", "ORACIÓN" -> PracticeTypes.PRAYER
        "MEDITATION", "MEDITACION", "MEDITACIÓN" -> PracticeTypes.GUIDED_MEDITATION
        "PAUSE" -> PracticeTypes.IMPULSE_PAUSE
        in PracticeTypes.ALL -> v
        else -> null
    }

    fun normalizePhase(raw: String): String? = when (val v = raw.trim().uppercase()) {
        "BREATH" -> "POSTURE_BREATH"
        in SegmentPhases.ALL -> v
        else -> null
    }

    fun normalizeJewel(raw: String): String? = when (val v = raw.trim().uppercase()) {
        "DARMA" -> "DHARMA"
        in Jewels.ALL -> v
        else -> null
    }

    /** Devuelve el motivo del rechazo, o null si la práctica es válida (REQ-008). */
    fun validate(dto: PracticeDto, seenIds: Set<String>): String? {
        val id = dto.id.trim()
        if (id.isEmpty()) return "id vacío"
        if (!ID_PATTERN.matches(id)) return "id con formato inválido"
        if (id in seenIds) return "id duplicado"
        val type = normalizeType(dto.type) ?: return "tipo desconocido: ${dto.type}"
        if (dto.title.isBlank()) return "título vacío"
        if (dto.segments.isEmpty()) return "sin segmentos"
        val orders = dto.segments.map { it.order }
        if (orders.any { it <= 0 } || orders.toSet().size != orders.size) return "orden de segmentos inválido"
        dto.segments.firstOrNull { normalizePhase(it.phase) == null }?.let { return "fase desconocida: ${it.phase}" }
        if (dto.meritDedication.isBlank()) return "sin dedicación del mérito"
        if (type == PracticeTypes.MANTRA) {
            val cfg = dto.mantraConfig ?: return "mantra sin mantra_config"
            if (cfg.targetRepetitions <= 0) return "target_repetitions inválido"
        }
        val path = dto.paramitaRef?.urlPath
        if (path != null && !path.startsWith("/")) return "url_path no empieza con /"
        return null
    }

    fun map(bundle: CatalogBundleDto, existingFavorites: Set<String>, nowEpochMs: Long): MappedCatalog {
        val practices = mutableListOf<PracticeEntity>()
        val segments = mutableListOf<PracticeSegmentEntity>()
        val configs = mutableListOf<MantraConfigEntity>()
        val tags = mutableListOf<PracticeTagEntity>()
        val jewels = mutableListOf<PracticeJewelFocusEntity>()
        val rejected = mutableListOf<Rejection>()
        val seen = mutableSetOf<String>()

        for (dto in bundle.practices) {
            val reason = validate(dto, seen)
            if (reason != null) {
                rejected += Rejection(dto.id, reason)
                continue
            }
            val id = dto.id.trim()
            seen += id
            val type = normalizeType(dto.type)!!

            practices += PracticeEntity(
                id = id,
                type = type,
                title = dto.title.trim(),
                subtitle = dto.subtitle?.trim()?.ifEmpty { null },
                tradition = dto.tradition?.trim()?.ifEmpty { null } ?: DEFAULT_TRADITION,
                estimatedMinutes = dto.estimatedMinutes.coerceAtLeast(1),
                audioAssetUri = dto.audioAssetUri?.trim()?.ifEmpty { null },
                ttsEnabled = dto.ttsEnabled,
                defaultPaceWpm = dto.defaultPaceWpm.takeIf { it > 0 } ?: 120,
                meritDedication = dto.meritDedication.trim(),
                paramitaSection = dto.paramitaRef?.section?.trim(),
                paramitaTopic = dto.paramitaRef?.topic?.trim(),
                paramitaUrlPath = dto.paramitaRef?.urlPath?.trim(),
                isFavorite = id in existingFavorites,
                isArchived = false,
                updatedAtEpochMs = nowEpochMs,
            )

            dto.segments.forEach { s ->
                segments += PracticeSegmentEntity(
                    id = "${id}_seg_${s.order}",
                    practiceId = id,
                    segmentOrder = s.order,
                    phase = normalizePhase(s.phase)!!,
                    heading = s.heading.trim(),
                    text = s.text.trim(),
                    transliteration = s.transliteration?.trim()?.ifEmpty { null },
                    meaning = s.meaning?.trim()?.ifEmpty { null },
                    pauseAfterSec = s.pauseAfterSec.coerceAtLeast(0),
                    repeatCount = s.repeatCount.coerceAtLeast(1),
                )
            }

            if (type == PracticeTypes.MANTRA) {
                val cfg = dto.mantraConfig!!
                configs += MantraConfigEntity(
                    practiceId = id,
                    targetRepetitions = cfg.targetRepetitions,
                    presetCountsJson = json.encodeToString(cfg.presetCounts.filter { it > 0 }),
                    recitationModesJson = json.encodeToString(cfg.recitationModes),
                    bellEveryN = cfg.bellEveryN.coerceAtLeast(0),
                    pauseBetweenRepsMs = cfg.pauseBetweenRepsMs.coerceAtLeast(0L),
                )
            }

            dto.tags.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()
                .forEach { tags += PracticeTagEntity(id, it) }
            dto.jewelFocus.mapNotNull { normalizeJewel(it) }.distinct()
                .forEach { jewels += PracticeJewelFocusEntity(id, it) }
        }
        return MappedCatalog(practices, segments, configs, tags, jewels, rejected)
    }
}
