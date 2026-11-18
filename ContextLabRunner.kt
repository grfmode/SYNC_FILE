package com.sync.app.domain.lab

import android.content.Context
import com.sync.app.domain.context.ContextAssembler
import com.sync.app.domain.context.ContextBudgetPlan
import com.sync.app.domain.context.ContextMode
import com.sync.app.domain.context.ContextSectionId
import com.sync.app.domain.knowledge.ChunkerRegistry
import com.sync.app.domain.knowledge.KnowledgeRetriever
import com.sync.app.domain.knowledge.KnowledgeStoreImpl
import com.sync.app.domain.memory.MemoryRetriever
import com.sync.app.domain.memory.MemoryStoreImpl
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Menjalankan perakitan konteks di atas database Lab.
 *
 * Hasilnya hanya laporan; tidak ada yang ditulis ke database utama. Pemulihan
 * database utama hanya bisa dipicu dari sini, dan hanya setelah snapshot
 * dinyatakan sah.
 */
@Singleton
class ContextLabRunner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val lab: LabDatabase
) {

    data class SectionReport(
        val section: ContextSectionId,
        val items: Int,
        val tokens: Int,
        val truncated: Int
    )

    data class Report(
        val mode: ContextMode,
        val contextLimit: Int?,
        val planUsable: Int,
        val estimatedTokens: Int,
        val overBudget: Boolean,
        val sections: List<SectionReport>,
        val preview: String,
        val durationMs: Long,
        val note: String? = null
    )

    sealed class Outcome {
        data class Success(val report: Report) : Outcome()
        data class Failure(val reason: String) : Outcome()
    }

    suspend fun run(
        basePrompt: String,
        layerRules: String,
        personalityPrompt: String,
        projectPrompt: String?,
        projectId: Long?,
        userPrompt: String,
        history: List<com.sync.app.model.ChatMessage>,
        summary: String?,
        mode: ContextMode,
        contextLimit: Int?
    ): Outcome {
        val database = when (val opened = lab.open()) {
            is LabDatabase.OpenResult.Failure -> return Outcome.Failure(opened.reason)
            is LabDatabase.OpenResult.Success -> opened.database
        }

        // Perakit khusus Lab: seluruh pembacaan memori dan knowledge memakai
        // database Lab, bukan database utama.
        val assembler = ContextAssembler(
            memoryRetriever = MemoryRetriever(MemoryStoreImpl(database.memoryDao())),
            knowledgeRetriever = KnowledgeRetriever(
                KnowledgeStoreImpl(context, database.knowledgeDao(), ChunkerRegistry())
            )
        )

        val plan = ContextBudgetPlan.from(contextLimit, mode)
        val promptBudget = com.sync.app.domain.chat.PromptBudget.forContextLimit(contextLimit)

        val started = System.currentTimeMillis()
        val result = runCatching {
            assembler.assemble(
                ContextAssembler.Input(
                    basePrompt = basePrompt,
                    layerRules = layerRules,
                    personalityPrompt = personalityPrompt,
                    projectPrompt = projectPrompt,
                    projectId = projectId,
                    userPrompt = userPrompt,
                    history = history,
                    summary = summary,
                    mode = mode,
                    plan = plan,
                    promptBudget = promptBudget
                )
            )
        }.getOrElse { return Outcome.Failure(it.message ?: "perakitan gagal") }

        val sections = ContextSectionId.entries.map { section ->
            val entries = result.log.filter { it.section == section }
            SectionReport(
                section = section,
                items = entries.count { it.tokens > 0 },
                tokens = entries.sumOf { it.tokens },
                truncated = entries.count { it.truncated }
            )
        }

        val knowledgeItems = sections.firstOrNull { it.section == ContextSectionId.KNOWLEDGE }?.items ?: 0
        val note = when {
            projectId == null -> "Project ID kosong: bagian Knowledge tidak ikut diuji."
            knowledgeItems == 0 ->
                "Tidak ada potongan knowledge untuk project $projectId di database Lab. Tekan \"Isi dari utama\" lalu jalankan lagi."
            else -> null
        }

        return Outcome.Success(
            Report(
                mode = mode,
                contextLimit = contextLimit,
                planUsable = plan.usable,
                estimatedTokens = result.estimatedInputTokens,
                overBudget = result.overBudget,
                sections = sections,
                preview = result.systemPrompt,
                durationMs = System.currentTimeMillis() - started,
                note = note
            )
        )
    }
}