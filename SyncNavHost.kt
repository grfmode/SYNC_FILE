package com.sync.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sync.app.ui.screens.chat.ChatScreen
import com.sync.app.ui.screens.knowledge.ProjectKnowledgeScreen
import com.sync.app.ui.screens.lab.ContextLabScreen
import com.sync.app.ui.screens.library.LibraryScreen
import com.sync.app.ui.screens.memory.MemoryScreen
import com.sync.app.ui.screens.projects.ProjectDetailScreen
import com.sync.app.ui.screens.projects.ProjectsScreen
import com.sync.app.ui.screens.projects.PromptLibraryScreen
import com.sync.app.ui.screens.settings.PersonalityScreen
import com.sync.app.ui.screens.settings.SettingsScreen

@Composable
fun SyncNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = SyncDestinations.CHAT
) {
    NavHost(navController = navController, startDestination = startDestination, modifier = modifier) {
        composable(SyncDestinations.CHAT) {
            ChatScreen(
                onOpenSettings = { navController.navigate(SyncDestinations.SETTINGS) },
                onOpenProjects = { navController.navigate(SyncDestinations.PROJECTS) },
                onOpenPrompts = { navController.navigate(SyncDestinations.PROMPTS) },
                onOpenLibrary = { navController.navigate(SyncDestinations.LIBRARY) }
            )
        }

        composable(
            route = SyncDestinations.CHAT_WITH_ID,
            arguments = listOf(navArgument("conversationId") { type = NavType.LongType })
        ) { entry ->
            ChatScreen(
                conversationId = entry.arguments?.getLong("conversationId"),
                onOpenSettings = { navController.navigate(SyncDestinations.SETTINGS) },
                onOpenProjects = { navController.navigate(SyncDestinations.PROJECTS) },
                onOpenPrompts = { navController.navigate(SyncDestinations.PROMPTS) },
                onOpenLibrary = { navController.navigate(SyncDestinations.LIBRARY) }
            )
        }

        composable(
            route = SyncDestinations.CHAT_NEW_IN_PROJECT,
            arguments = listOf(navArgument("projectId") { type = NavType.LongType })
        ) { entry ->
            ChatScreen(
                projectId = entry.arguments?.getLong("projectId"),
                onOpenSettings = { navController.navigate(SyncDestinations.SETTINGS) },
                onOpenProjects = { navController.navigate(SyncDestinations.PROJECTS) },
                onOpenPrompts = { navController.navigate(SyncDestinations.PROMPTS) },
                onOpenLibrary = { navController.navigate(SyncDestinations.LIBRARY) }
            )
        }

        composable(SyncDestinations.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenPersonality = { navController.navigate(SyncDestinations.PERSONALITY) },
                onOpenMemory = { navController.navigate(SyncDestinations.memory(null)) },
                onOpenLab = { navController.navigate(SyncDestinations.CONTEXT_LAB) }
            )
        }

        composable(SyncDestinations.PERSONALITY) {
            PersonalityScreen(onBack = { navController.popBackStack() })
        }

        composable(SyncDestinations.PROJECTS) {
            ProjectsScreen(
                onBack = { navController.popBackStack() },
                onOpenProjectDetail = { navController.navigate(SyncDestinations.projectDetail(it)) }
            )
        }

        composable(
            route = SyncDestinations.PROJECT_DETAIL,
            arguments = listOf(navArgument("projectId") { type = NavType.LongType })
        ) { entry ->
            val projectId = entry.arguments?.getLong("projectId") ?: return@composable
            ProjectDetailScreen(
                projectId = projectId,
                onBack = { navController.popBackStack() },
                onOpenChat = { navController.navigate(SyncDestinations.chatWithId(it)) },
                onOpenNewChatInProject = { navController.navigate(SyncDestinations.chatNewInProject(it)) },
                onOpenKnowledge = { navController.navigate(SyncDestinations.knowledge(it)) },
                onOpenMemory = { navController.navigate(SyncDestinations.memory(it)) }
            )
        }

        composable(
            route = SyncDestinations.MEMORY,
            arguments = listOf(navArgument("projectId") {
                type = NavType.LongType
                defaultValue = -1L
            })
        ) { entry ->
            val projectId = entry.arguments?.getLong("projectId")?.takeIf { it > 0 }
            MemoryScreen(projectId = projectId, onBack = { navController.popBackStack() })
        }

        composable(
            route = SyncDestinations.PROJECT_KNOWLEDGE,
            arguments = listOf(navArgument("projectId") { type = NavType.LongType })
        ) {
            ProjectKnowledgeScreen(onBack = { navController.popBackStack() })
        }

        composable(SyncDestinations.CONTEXT_LAB) {
            ContextLabScreen(onBack = { navController.popBackStack() })
        }

        composable(SyncDestinations.PROMPTS) {
            PromptLibraryScreen(onBack = { navController.popBackStack() })
        }

        composable(SyncDestinations.LIBRARY) {
            LibraryScreen(onBack = { navController.popBackStack() })
        }
    }
}