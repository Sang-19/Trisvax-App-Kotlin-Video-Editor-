package com.trisvax.studio.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.trisvax.studio.ui.editor.EditorScreen
import com.trisvax.studio.ui.editor.EditorViewModel
import com.trisvax.studio.ui.export.SaveConfirmationScreen
import com.trisvax.studio.ui.picker.CameraCaptureScreen
import com.trisvax.studio.ui.picker.MediaPickerScreen
import com.trisvax.studio.ui.projects.ProjectsScreen
import com.trisvax.studio.ui.projects.ProjectsViewModel
import com.trisvax.studio.ui.welcome.WelcomeScreen

object TrisvaxRoutes {
    const val WELCOME = "welcome"
    const val PROJECTS = "projects"
    const val PICKER = "picker"
    const val CAMERA = "camera"
    const val EDITOR = "editor/{projectId}"
    const val FINISHED = "finished"

    fun editor(projectId: String): String = "editor/$projectId"
}

@Composable
fun TrisvaxNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = TrisvaxRoutes.WELCOME
    ) {
        composable(TrisvaxRoutes.WELCOME) {
            WelcomeScreen(
                onOpenStudio = {
                    navController.navigate(TrisvaxRoutes.PROJECTS) {
                        popUpTo(TrisvaxRoutes.WELCOME) { inclusive = true }
                    }
                }
            )
        }

        composable(TrisvaxRoutes.PROJECTS) {
            val vm: ProjectsViewModel = viewModel()
            ProjectsScreen(
                viewModel = vm,
                onOpenProject = { pid ->
                    navController.navigate(TrisvaxRoutes.editor(pid))
                },
                onOpenMediaPicker = {
                    navController.navigate(TrisvaxRoutes.PICKER)
                },
                onOpenCamera = {
                    navController.navigate(TrisvaxRoutes.CAMERA)
                }
            )
        }

        composable(TrisvaxRoutes.PICKER) {
            MediaPickerScreen(
                onBack = { navController.popBackStack() },
                onProjectCreated = { pid ->
                    navController.navigate(TrisvaxRoutes.editor(pid)) {
                        popUpTo(TrisvaxRoutes.PICKER) { inclusive = true }
                    }
                }
            )
        }

        composable(TrisvaxRoutes.CAMERA) {
            CameraCaptureScreen(
                onBack = { navController.popBackStack() },
                onVideoRecorded = { pid ->
                    navController.navigate(TrisvaxRoutes.editor(pid)) {
                        popUpTo(TrisvaxRoutes.CAMERA) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = TrisvaxRoutes.EDITOR,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val pid = backStackEntry.arguments?.getString("projectId").orEmpty()
            val vm: EditorViewModel = viewModel()
            EditorScreen(
                projectId = pid,
                viewModel = vm,
                onClose = { navController.popBackStack() },
                onExportFinished = {
                    navController.navigate(TrisvaxRoutes.FINISHED)
                }
            )
        }

        composable(TrisvaxRoutes.FINISHED) {
            SaveConfirmationScreen(
                onGoToHome = {
                    navController.navigate(TrisvaxRoutes.PROJECTS) {
                        popUpTo(TrisvaxRoutes.PROJECTS) { inclusive = true }
                    }
                }
            )
        }
    }
}
