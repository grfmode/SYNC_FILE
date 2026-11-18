package com.sync.app.ui.screens.lab

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sync.app.domain.backup.BackupDirectory
import com.sync.app.domain.backup.BackupSelfTest
import com.sync.app.domain.backup.HotBackupManager
import com.sync.app.domain.backup.RestoreRecovery
import com.sync.app.domain.context.ContextMode
import com.sync.app.domain.lab.ContextLabRunner
import com.sync.app.domain.lab.LabDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ContextLabViewModel @Inject constructor(
    private val lab: LabDatabase,
    private val runner: ContextLabRunner,
    private val hotBackup: HotBackupManager,
    private val selfTest: BackupSelfTest,
    private val recovery: RestoreRecovery,
    private val directory: BackupDirectory
) : ViewModel() {

    private val _report = MutableStateFlow<ContextLabRunner.Report?>(null)
    val report: StateFlow<ContextLabRunner.Report?> = _report.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _selfTestResult = MutableStateFlow<String?>(null)
    val selfTestResult: StateFlow<String?> = _selfTestResult.asStateFlow()

    val isolated: Boolean get() = lab.isIsolated()
    val safeMode: Boolean get() = recovery.safeMode

    fun seed() {
        viewModelScope.launch {
            lab.seedFromMain()
                .onSuccess { _message.value = "Lab diisi dari database utama." }
                .onFailure { _message.value = "Gagal mengisi Lab: ${it.message}" }
        }
    }

    fun run(mode: ContextMode, limit: Int?, prompt: String, projectId: Long?) {
        viewModelScope.launch {
            when (val outcome = runner.run(
                basePrompt = "BASE PROMPT (uji)",
                layerRules = "LAYER RULES (uji)",
                personalityPrompt = "",
                projectPrompt = null,
                projectId = projectId,
                userPrompt = prompt,
                history = emptyList(),
                summary = null,
                mode = mode,
                contextLimit = limit
            )) {
                is ContextLabRunner.Outcome.Success -> _report.value = outcome.report
                is ContextLabRunner.Outcome.Failure -> _message.value = outcome.reason
            }
        }
    }

    fun selfTestBackup() {
        viewModelScope.launch {
            val report = selfTest.run()
            _selfTestResult.value = if (report.passed) {
                "Self-test lolos (${report.cases.size} kasus)."
            } else {
                "Self-test gagal: ${report.cases.filter { !it.passed }.joinToString { "${it.name} → ${it.detail}" }}"
            }
        }
    }

    fun hotBackupNow() {
        viewModelScope.launch {
            val result = hotBackup.create("manual dari Lab")
            _message.value = if (result.ok) "Cadangan dibuat: ${result.directory}" else "Cadangan gagal: ${result.error}"
        }
    }

    fun restoreLatest() {
        viewModelScope.launch {
            val latest = directory.root().listFiles()
                ?.filter { it.isDirectory && File(it, "manifest.json").exists() }
                ?.sortedByDescending { it.name }
                ?.firstOrNull()
            if (latest == null) {
                _message.value = "Belum ada cadangan."
                return@launch
            }
            recovery.restore(File(latest, "snapshot.db").absolutePath)
            _message.value = "Pemulihan dijalankan. Status: ${if (recovery.safeMode) "SAFE_MODE" else "selesai"}"
        }
    }

    fun resetLab() {
        lab.reset()
        _report.value = null
        _message.value = "Lab direset."
    }

    fun clearMessage() { _message.value = null }
}