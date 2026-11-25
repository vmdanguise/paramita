package ar.param.advisor.ui.components

import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ar.param.advisor.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/** Los 8 símbolos auspiciosos (Ashtamangala), dibujados de cero para Mani. */
enum class AuspiciousSymbol(@DrawableRes val drawable: Int, @StringRes val label: Int) {
    PARASOL(R.drawable.auspicious_parasol, R.string.auspicious_parasol),
    FISHES(R.drawable.auspicious_fishes, R.string.auspicious_fishes),
    VASE(R.drawable.auspicious_vase, R.string.auspicious_vase),
    LOTUS(R.drawable.auspicious_lotus, R.string.auspicious_lotus),
    CONCH(R.drawable.auspicious_conch, R.string.auspicious_conch),
    KNOT(R.drawable.auspicious_knot, R.string.auspicious_knot),
    BANNER(R.drawable.auspicious_banner, R.string.auspicious_banner),
    WHEEL(R.drawable.auspicious_wheel, R.string.auspicious_wheel),
    ;

    companion object {
        /** Uno distinto cada día, el mismo durante todo el día. */
        fun forDay(date: LocalDate = LocalDate.now()): AuspiciousSymbol =
            entries[Math.floorMod(date.toEpochDay(), entries.size.toLong()).toInt()]

        /** Para elegir uno estable a partir de un texto (por ejemplo, el id de una práctica). */
        fun forKey(key: String): AuspiciousSymbol = entries[Math.floorMod(key.hashCode(), entries.size)]
    }
}

@Composable
fun AuspiciousImage(symbol: AuspiciousSymbol, modifier: Modifier = Modifier, decorative: Boolean = false) {
    Image(
        painter = painterResource(symbol.drawable),
        contentDescription = if (decorative) null else stringResource(symbol.label),
        modifier = modifier,
    )
}

/** Los símbolos van rotando suavemente, uno cada [intervalMs]. */
@Composable
fun AuspiciousCarousel(modifier: Modifier = Modifier, intervalMs: Long = 9_000L, start: AuspiciousSymbol = AuspiciousSymbol.forDay()) {
    var index by rememberSaveable { mutableIntStateOf(start.ordinal) }
    LaunchedEffect(intervalMs) {
        while (true) {
            delay(intervalMs)
            index = (index + 1) % AuspiciousSymbol.entries.size
        }
    }
    Crossfade(targetState = AuspiciousSymbol.entries[index], animationSpec = tween(1_200), label = "símbolos", modifier = modifier) {
        AuspiciousImage(it, Modifier.fillMaxSize())
    }
}

/** Carga una imagen propia de la carpeta privada de la app, fuera del hilo principal. */
@Composable
fun rememberLocalImage(path: String?): ImageBitmap? {
    val image by produceState<ImageBitmap?>(initialValue = null, path) {
        value = if (path == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching { File(path).takeIf { it.isFile }?.let { BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() } }.getOrNull()
            }
        }
    }
    return image
}

/**
 * La imagen de una práctica: la que eligió el usuario o, si no tiene, un símbolo auspicioso
 * (fijo, o rotando si [rotateWhenNoImage]).
 */
@Composable
fun PracticeArtwork(
    imagePath: String?,
    fallbackKey: String,
    modifier: Modifier = Modifier,
    rotateWhenNoImage: Boolean = false,
    cornerRadiusDp: Int = 16,
) {
    val image = rememberLocalImage(imagePath)
    Box(modifier.clip(RoundedCornerShape(cornerRadiusDp.dp)), contentAlignment = Alignment.Center) {
        when {
            image != null -> Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            rotateWhenNoImage -> AuspiciousCarousel(Modifier.fillMaxSize())
            else -> AuspiciousImage(AuspiciousSymbol.forKey(fallbackKey), Modifier.fillMaxSize(), decorative = true)
        }
    }
}
