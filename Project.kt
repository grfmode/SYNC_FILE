package com.sync.app.model

data class Project(
    val id: Long,
    val name: String,
    val description: String? = null,
    val systemPrompt: String? = null,
    val memoryText: String? = null, // [P-030c]
    val createdAt: Long
)