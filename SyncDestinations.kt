package com.sync.app.ui.navigation

object SyncDestinations {
    const val CHAT = "chat"
    const val SETTINGS = "settings"
    const val PERSONALITY = "personality"
    const val PROJECTS = "projects"
    const val PROMPTS = "prompts"
    const val LIBRARY = "library"

    const val PROJECT_DETAIL = "project_detail/{projectId}"
    const val CHAT_WITH_ID = "chat/{conversationId}"
    const val CHAT_NEW_IN_PROJECT = "chat_new?projectId={projectId}"

    // [Baru] memori, knowledge, lab
    const val MEMORY = "memory?projectId={projectId}"
    const val PROJECT_KNOWLEDGE = "knowledge/{projectId}"
    const val CONTEXT_LAB = "lab"

    fun projectDetail(projectId: Long) = "project_detail/$projectId"
    fun chatWithId(conversationId: Long) = "chat/$conversationId"
    fun chatNewInProject(projectId: Long) = "chat_new?projectId=$projectId"
    fun memory(projectId: Long?) = "memory?projectId=${projectId ?: -1L}"
    fun knowledge(projectId: Long) = "knowledge/$projectId"
}