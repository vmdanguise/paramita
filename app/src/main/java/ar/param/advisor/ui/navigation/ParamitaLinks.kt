package ar.param.advisor.ui.navigation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/** Lista blanca de enlaces (spec de ética CON-001, ADR-0005). No se abre ninguna otra URL. */
object ParamitaLinks {
    const val HOME = "https://www.paramita.org/"
    const val MEDITA_CON_NOSOTROS = "https://www.paramita.org/medita-con-nosotros/"
    const val CURSOS = "https://cursos.paramita.org/"
    val ALLOWED = setOf(HOME, MEDITA_CON_NOSOTROS, CURSOS)
}

fun openAllowedLink(context: Context, url: String) {
    if (url !in ParamitaLinks.ALLOWED) return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // Sin navegador instalado: no hay nada que hacer.
    }
}
