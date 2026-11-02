package app.aura.ui

import android.content.Context
import android.net.Uri
import app.aura.core.AuraEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Single source of truth shared by all screens and the theme. Everything here
 * comes from the real engine — no scripted placeholder data.
 */
object EngineState {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _engineReady = MutableStateFlow(false)
    val engineReady: StateFlow<Boolean> = _engineReady

    private val _modelLoaded = MutableStateFlow(false)
    val modelLoaded: StateFlow<Boolean> = _modelLoaded

    /** {"name","contextLength","quantization","sizeBytes"} or null. */
    private val _modelInfo = MutableStateFlow<JSONObject?>(null)
    val modelInfo: StateFlow<JSONObject?> = _modelInfo

    private val _agentStatus = MutableStateFlow("idle")
    val agentStatus: StateFlow<String> = _agentStatus

    private val _conversationCount = MutableStateFlow(0L)
    val conversationCount: StateFlow<Long> = _conversationCount

    private val _maxIterations = MutableStateFlow(20)
    val maxIterations: StateFlow<Int> = _maxIterations

    private val _isDarkTheme = MutableStateFlow(true)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme

    /** Light theme survives process restarts via a one-entry preferences file. */
    fun loadPreferences(context: Context) {
        val prefs = context.getSharedPreferences("aura-ui", Context.MODE_PRIVATE)
        _isDarkTheme.value = prefs.getBoolean("isDarkTheme", true)
        _maxIterations.value = prefs.getInt("maxIterations", 20)
    }

    fun setDarkTheme(context: Context, dark: Boolean) {
        _isDarkTheme.value = dark
        context.getSharedPreferences("aura-ui", Context.MODE_PRIVATE)
            .edit().putBoolean("isDarkTheme", dark).apply()
    }

    fun setMaxIterationsStored(context: Context, n: Int) {
        _maxIterations.value = n
        context.getSharedPreferences("aura-ui", Context.MODE_PRIVATE)
            .edit().putInt("maxIterations", n).apply()
    }

    /** Pull a fresh status snapshot from the engine (blocking). */
    suspend fun refreshStatus() = withContext(Dispatchers.IO) {
        try {
            val status = JSONObject(AuraEngine.getStatusJson())
            _agentStatus.value = status.optString("agentStatus", "idle")
            _modelLoaded.value = status.optBoolean("modelLoaded", false)
            _conversationCount.value = status.optLong("conversationCount", 0)
            _maxIterations.value = status.optInt("maxIterations", 20)
            _engineReady.value = !status.has("error")
        } catch (_: Throwable) {
            _engineReady.value = false
        }
    }

    /** Initialize the persisted engine and sync the status snapshot. */
    fun initializeAsync(context: Context) {
        loadPreferences(context)
        scope.launch {
            AuraEngine.initialize(context.filesDir.absolutePath)
            AuraEngine.setMaxIterations(_maxIterations.value)
            refreshStatus()
        }
    }

    /**
     * Copy a SAF-picked file into private storage and load it as a GGUF model.
     * Returns the load result JSON from [AuraEngine.loadGguf].
     */
    suspend fun importAndLoadModel(context: Context, uri: Uri): String =
        withContext(Dispatchers.IO) {
            try {
                val displayName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
                } ?: "model.gguf"

                if (!displayName.endsWith(".gguf", ignoreCase = true)) {
                    return@withContext JSONObject()
                        .put("ok", false)
                        .put("error", "Picked file is not a .gguf model file")
                        .toString()
                }

                val modelsDir = java.io.File(context.filesDir, "models").also { it.mkdirs() }
                val target = java.io.File(modelsDir, displayName)
                val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                if (bytes == null) {
                    return@withContext JSONObject()
                        .put("ok", false)
                        .put("error", "Cannot read the selected file")
                        .toString()
                }

                val result = JSONObject(AuraEngine.loadGguf(target.absolutePath))
                // Space is limited on devices: keep only the active model file.
                if (!result.optBoolean("ok", false)) {
                    target.delete()
                    return@withContext result.toString()
                }
                modelsDir.listFiles()?.forEach { file ->
                    if (file != target) file.delete()
                }

                refreshStatus()
                _modelLoaded.value = result.optBoolean("ok", false)
                _modelInfo.value = result
                result.toString()
            } catch (e: Throwable) {
                JSONObject()
                    .put("ok", false)
                    .put("error", e.message ?: "model import failed")
                    .toString()
            }
        }
}
