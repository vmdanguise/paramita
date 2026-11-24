package ar.param.advisor.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ar.param.advisor.ui.ManiViewModel
import ar.param.advisor.ui.about.FaqScreen
import ar.param.advisor.ui.home.RefugeHomeScreen
import ar.param.advisor.ui.practice.PracticeRunnerScreen
import ar.param.advisor.ui.wellbeing.ImpulsePauseActivity
import ar.param.advisor.ui.wellbeing.MindfulAppTargetsScreen

object ManiRoutes {
    const val HOME = "home"
    const val PRACTICE = "practice/{practiceId}"
    const val IMPULSE_PAUSE = "impulse_pause"
    const val MINDFUL_TARGETS = "mindful_targets"
    const val THREE_JEWELS = "three_jewels?jewel={jewel}"
    const val FAQ = "faq"
    fun practiceRoute(practiceId: String) = "practice/$practiceId"
}

/** Pedido único de navegación hecho desde fuera de la UI (arranque o notificación). */
data class NavRequest(val practiceId: String, val token: Long = System.nanoTime())

@Composable
fun ManiNavGraph(
    viewModel: ManiViewModel,
    navRequest: NavRequest?,
    onNavRequestHandled: () -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    LaunchedEffect(navRequest) {
        val request = navRequest ?: return@LaunchedEffect
        val route = ManiRoutes.practiceRoute(request.practiceId)
        if (navController.currentDestination?.route != ManiRoutes.PRACTICE ||
            navController.currentBackStackEntry?.arguments?.getString("practiceId") != request.practiceId
        ) {
            navController.navigate(route) { launchSingleTop = true }
        }
        onNavRequestHandled()
    }

    NavHost(navController = navController, startDestination = ManiRoutes.HOME) {
        composable(ManiRoutes.HOME) {
            val state by viewModel.homeUiState.collectAsStateWithLifecycle()
            val context = LocalContext.current
            RefugeHomeScreen(
                state = state,
                onOpenPractice = { navController.navigate(ManiRoutes.practiceRoute(it)) },
                onOpenFaq = { navController.navigate(ManiRoutes.FAQ) },
                onOpenImpulsePause = { context.startActivity(ImpulsePauseActivity.intent(context)) },
                onOpenMindfulTargets = { navController.navigate(ManiRoutes.MINDFUL_TARGETS) },
                onStartWithRefugeChange = viewModel::setStartWithRefuge,
                onOpenLink = { openAllowedLink(context, it) },
            )
        }
        composable(
            route = ManiRoutes.PRACTICE,
            arguments = listOf(navArgument("practiceId") { type = NavType.StringType }),
        ) { entry ->
            val practiceId = entry.arguments?.getString("practiceId").orEmpty()
            PracticeRunnerScreen(
                practiceId = practiceId,
                viewModel = viewModel,
                onBack = {
                    if (!navController.popBackStack(ManiRoutes.HOME, inclusive = false)) {
                        navController.navigate(ManiRoutes.HOME)
                    }
                },
            )
        }
        composable(ManiRoutes.MINDFUL_TARGETS) {
            val targets by viewModel.mindfulAppTargets.collectAsStateWithLifecycle()
            val context = LocalContext.current
            MindfulAppTargetsScreen(
                targets = targets,
                onSave = viewModel::saveMindfulAppTarget,
                onDelete = viewModel::deleteMindfulAppTarget,
                onTryPause = { context.startActivity(ImpulsePauseActivity.intent(context)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(ManiRoutes.FAQ) {
            val context = LocalContext.current
            FaqScreen(
                onBack = { navController.popBackStack() },
                onOpenLink = { openAllowedLink(context, it) },
            )
        }
    }
}
