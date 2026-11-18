package com.sync.app.ui.screens.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale

data class AttachmentLaunchers(
    val launchCamera: () -> Unit,
    val launchGallery: () -> Unit,
    val launchDocument: () -> Unit
)

data class SttState(
    val isListening: Boolean,
    val rmsDb: Float,
    val toggleListening: () -> Unit
)

/**
 * Helper untuk mengelola Launcher Kamera, Galeri, dan Dokumen.
 */
@Composable
fun rememberAttachmentLaunchers(
    onUriPicked: (Uri) -> Unit,
    onError: () -> Unit
): AttachmentLaunchers {
    val context = LocalContext.current
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val currentOnUriPicked by rememberUpdatedState(onUriPicked)
    val currentOnError by rememberUpdatedState(onError)

    fun deliverPersistableUri(uri: Uri) {
        // Persistable grant tersedia pada SAF dan Photo Picker Android modern.
        // Beberapa fallback provider hanya memberi grant sementara, jadi kegagalan
        // persist tidak boleh menggagalkan pemilihan file saat itu.
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Abaikan hanya kegagalan persist; callback tetap memproses URI.
        }
        currentOnUriPicked(uri)
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) deliverPersistableUri(uri) }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) deliverPersistableUri(uri) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success -> if (success && cameraUri != null) currentOnUriPicked(cameraUri!!) }

    return remember {
        AttachmentLaunchers(
            launchCamera = {
                try {
                    val tempFile = File.createTempFile("camera_", ".jpg", context.cacheDir)
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
                    cameraUri = uri
                    cameraLauncher.launch(uri)
                } catch (e: Exception) {
                    currentOnError()
                }
            },
            launchGallery = {
                imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            launchDocument = {
                documentPickerLauncher.launch(arrayOf("*/*"))
            }
        )
    }
}

/**
 * Helper untuk mengelola STT (SpeechRecognizer) beserta animasi volume (rmsDb).
 */
@Composable
fun rememberSpeechRecognizer(
    onResult: (String) -> Unit,
    onPermissionDenied: () -> Unit
): SttState {
    val context = LocalContext.current
    var isListening by remember { mutableStateOf(false) }
    var rmsDb by remember { mutableFloatStateOf(0f) }
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnPermissionDenied by rememberUpdatedState(onPermissionDenied)

    DisposableEffect(context) {
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            
            override fun onRmsChanged(rmsdB: Float) {
                rmsDb = rmsdB
            }
            
            override fun onEndOfSpeech() {
                isListening = false
                rmsDb = 0f
            }
            override fun onError(error: Int) {
                isListening = false
                rmsDb = 0f
            }
            override fun onResults(results: Bundle?) {
                isListening = false
                rmsDb = 0f
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    currentOnResult(matches[0])
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        speechRecognizer = recognizer
        onDispose {
            recognizer.destroy()
            speechRecognizer = null
        }
    }

    val requestMicPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            }
            speechRecognizer?.startListening(intent)
            isListening = true
        } else {
            currentOnPermissionDenied()
        }
    }

    return SttState(
        isListening = isListening,
        rmsDb = rmsDb,
        toggleListening = {
            if (isListening) {
                speechRecognizer?.stopListening()
                isListening = false
                rmsDb = 0f
            } else {
                val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    }
                    speechRecognizer?.startListening(intent)
                    isListening = true
                } else {
                    requestMicPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
    )
}

/**
 * Helper untuk mengelola TextToSpeech (TTS).
 */
@Composable
fun rememberTextToSpeech(): (String) -> Unit {
    val context = LocalContext.current
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(Unit) {
        val textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale("id", "ID"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.getDefault())
                }
            }
        }
        tts = textToSpeech
        onDispose {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
    }

    return remember {
        { text: String ->
            val cleanText = text.replace(Regex("[#*`_>\\[\\]()]"), "")
            tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "SYNC_TTS")
        }
    }
}