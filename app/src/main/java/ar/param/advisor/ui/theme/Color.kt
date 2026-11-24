package ar.param.advisor.ui.theme

import androidx.compose.ui.graphics.Color

// Paleta tomada del kit global de paramita.org (Elementor, post-191049.css), relevada el 2026-11-24.
internal object ParamitaPalette {
    val Blue = Color(0xFF115EAA)          // primary del sitio
    val Sky = Color(0xFF0196C8)           // secondary del sitio
    val SkyBright = Color(0xFF2EA3F2)
    val Aqua = Color(0xFF00C7E5)
    val AquaPale = Color(0xFFB2EEF8)
    val IcePale = Color(0xFFCDECF8)
    val Navy = Color(0xFF0D4277)
    val NavyDeep = Color(0xFF003D6B)
    val NavyInk = Color(0xFF003F6E)
    val Gold = Color(0xFFFFAE00)          // accent del sitio
    val GoldWarm = Color(0xFFFFC507)
    val GoldPale = Color(0xFFFFE8B5)
    val Cream = Color(0xFFFEF7E5)
    val Sand = Color(0xFFF2E6D8)
    val Umber = Color(0xFF662C00)
    val Ink = Color(0xFF212121)           // text del sitio
    val Slate = Color(0xFF40484A)
    val Mist = Color(0xFF89ABC1)
    val Brick = Color(0xFFB32025)
    val Ember = Color(0xFFE75025)
    val White = Color(0xFFFFFFFF)
}

// Superficies del tema oscuro: derivadas del azul del sitio (no existen en paramita.org).
internal object ManiDarkSurfaces {
    val Background = Color(0xFF0A1C2E)
    val Surface = Color(0xFF0F2438)
    val SurfaceVariant = Color(0xFF1B3550)
    val OnError = Color(0xFF0A0A0A)   // con Ink (#212121) sobre Ember no llega a 4.5:1
}
