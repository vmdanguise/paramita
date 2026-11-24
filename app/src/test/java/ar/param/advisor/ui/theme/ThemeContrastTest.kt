package ar.param.advisor.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** REQ-051 (spec-design-ui-navegacion): contraste WCAG AA ≥ 4.5:1 en los pares de texto, en los dos temas. */
class ThemeContrastTest {

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun textPairs(s: ColorScheme) = mapOf(
        "onPrimary/primary" to (s.onPrimary to s.primary),
        "onPrimaryContainer/primaryContainer" to (s.onPrimaryContainer to s.primaryContainer),
        "onSecondary/secondary" to (s.onSecondary to s.secondary),
        "onSecondaryContainer/secondaryContainer" to (s.onSecondaryContainer to s.secondaryContainer),
        "onTertiary/tertiary" to (s.onTertiary to s.tertiary),
        "onTertiaryContainer/tertiaryContainer" to (s.onTertiaryContainer to s.tertiaryContainer),
        "onBackground/background" to (s.onBackground to s.background),
        "onSurface/surface" to (s.onSurface to s.surface),
        "onSurfaceVariant/surfaceVariant" to (s.onSurfaceVariant to s.surfaceVariant),
        "primary/background" to (s.primary to s.background),
        "onError/error" to (s.onError to s.error),
    )

    private fun assertAa(name: String, scheme: ColorScheme) {
        val failures = textPairs(scheme)
            .mapValues { (_, p) -> contrast(p.first, p.second) }
            .filterValues { it < 4.5 }
        assertTrue("Pares bajo 4.5:1 en $name: $failures", failures.isEmpty())
    }

    @Test
    fun lightSchemeMeetsAa() = assertAa("claro", ManiLightColors)

    @Test
    fun darkSchemeMeetsAa() = assertAa("oscuro", ManiDarkColors)
}
