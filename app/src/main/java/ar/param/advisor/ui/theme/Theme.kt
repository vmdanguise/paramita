package ar.param.advisor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

internal val ManiLightColors: ColorScheme = lightColorScheme(
    primary = ParamitaPalette.Blue,
    onPrimary = ParamitaPalette.White,
    primaryContainer = ParamitaPalette.IcePale,
    onPrimaryContainer = ParamitaPalette.NavyDeep,
    secondary = ParamitaPalette.Navy,
    onSecondary = ParamitaPalette.White,
    secondaryContainer = ParamitaPalette.AquaPale,
    onSecondaryContainer = ParamitaPalette.NavyInk,
    tertiary = ParamitaPalette.Gold,
    onTertiary = ParamitaPalette.Ink,
    tertiaryContainer = ParamitaPalette.GoldPale,
    onTertiaryContainer = ParamitaPalette.Umber,
    background = ParamitaPalette.Cream,
    onBackground = ParamitaPalette.Ink,
    surface = ParamitaPalette.Cream,
    onSurface = ParamitaPalette.Ink,
    surfaceVariant = ParamitaPalette.Sand,
    onSurfaceVariant = ParamitaPalette.Slate,
    outline = ParamitaPalette.Mist,
    error = ParamitaPalette.Brick,
    onError = ParamitaPalette.White,
)

internal val ManiDarkColors: ColorScheme = darkColorScheme(
    primary = ParamitaPalette.SkyBright,
    onPrimary = ManiDarkSurfaces.Background,
    primaryContainer = ParamitaPalette.Navy,
    onPrimaryContainer = ParamitaPalette.IcePale,
    secondary = ParamitaPalette.Aqua,
    onSecondary = ManiDarkSurfaces.Background,
    secondaryContainer = ParamitaPalette.NavyInk,
    onSecondaryContainer = ParamitaPalette.AquaPale,
    tertiary = ParamitaPalette.GoldWarm,
    onTertiary = ParamitaPalette.Ink,
    tertiaryContainer = ParamitaPalette.Umber,
    onTertiaryContainer = ParamitaPalette.GoldPale,
    background = ManiDarkSurfaces.Background,
    onBackground = ParamitaPalette.Cream,
    surface = ManiDarkSurfaces.Surface,
    onSurface = ParamitaPalette.Cream,
    surfaceVariant = ManiDarkSurfaces.SurfaceVariant,
    onSurfaceVariant = ParamitaPalette.IcePale,
    outline = ParamitaPalette.Mist,
    error = ParamitaPalette.Ember,
    onError = ManiDarkSurfaces.OnError,
)

/** Tema de Mani. Sin dynamicColor: la identidad visual se mantiene estable (REQ-040). */
@Composable
fun ManiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) ManiDarkColors else ManiLightColors,
        typography = ManiTypography,
        content = content,
    )
}
