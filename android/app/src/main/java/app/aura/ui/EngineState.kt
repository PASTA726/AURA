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

    /** Gate every screen waits on: engine init (incl. model auto-reload) done. */
    private val engineInitialized = kotlinx.coroutines.CompletableDeferred<Unit>()

    /** Suspends until the persisted engine is ready. Call before engine I/O. */
    suspend fun awaitEngine() {
        engineInitialized.await()
    }

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

    /** Slider is being dragged: update the UI value only (B9 guard). */
    fun setMaxIterationsPreview(n: Int) {
        _maxIterationsBeingEdited = true
        _maxIterations.value = n
    }

    /** Slider released: commit to prefs AND push to the engine (B9). */
    fun setMaxIterationsCommit(context: Context, n: Int) {
        _maxIterationsBeingEdited = false
        _maxIterations.value = n
        context.getSharedPreferences("aura-ui", Context.MODE_PRIVATE)
            .edit().putInt("maxIterations", n).apply()
        scope.launch(Dispatchers.IO) {
            AuraEngine.setMaxIterations(n)
        }
    }

    /** Pull a fresh status snapshot from the engine (blocking). */
    suspend fun refreshStatus() = withContext(Dispatchers.IO) {
        try {
            val status = JSONObject(AuraEngine.getStatusJson())
            _agentStatus.value = status.optString("agentStatus", "idle")
            _modelLoaded.value = status.optBoolean("modelLoaded", false)
            _conversationCount.value = status.optLong("conversationCount", 0)
            // Don't clobber a value the user is actively dragging in Settings;
            // the engine and prefs only diverge transiently.
            if (!_maxIterationsBeingEdited) {
                _maxIterations.value = status.optInt("maxIterations", 20)
            }
            _engineReady.value = !status.has("error")
        } catch (_: Throwable) {
            _engineReady.value = false
        }
    }

    @Volatile
    private var _maxIterationsBeingEdited = false

    /**
     * Initialize the persisted engine and sync the status snapshot. If a GGUF
     * model was loaded in a previous session, it is re-loaded now (B5): the
     * process restart loses the llama.cpp state, but the file persists under
     * filesDir/models.
     */
    fun initializeAsync(context: Context) {
        loadPreferences(context)
        scope.launch {
            val ready = AuraEngine.initialize(context.filesDir.absolutePath)
            if (ready) {
                AuraEngine.setMaxIterations(_maxIterations.value)
                autoReloadModel(context)
            }
            refreshStatus()
            engineInitialized.complete(Unit)
        }
    }

    /// Reload the persisted model file after a process restart (best effort).
    private suspend fun autoReloadModel(context: Context) = withContext(Dispatchers.IO) {
        try {
            val modelsDir = java.io.File(context.filesDir, "models")
            val existing = modelsDir.listFiles { f -> f.name.endsWith(".gguf", ignoreCase = true) }
                ?.maxByOrNull { it.lastModified() } ?: return@withContext
            val result = JSONObject(AuraEngine.loadGguf(existing.absolutePath))
            if (result.optBoolean("ok", false)) {
                _modelInfo.value = result
                _modelLoaded.value = true
            } else {
                // Corrupted or incompatible file: drop it so the user isn't
                // stuck failing to load the same broken model every launch.
                existing.delete()
            }
        } catch (_: Throwable) {
        }
    }

    /**
     * Copy a SAF-picked file into private storage and load it as a GGUF model.
     * Returns the load result JSON from [AuraEngine.loadGguf].
     */
    suspend fun importAndLoadModel(context: Context, uri: Uri): String =
        withContext(Dispatchers.IO) {
            try {
                awaitEngine()
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
