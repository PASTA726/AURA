package app.aura.core

import android.util.Log

object AuraEngine {
    private const val TAG = "AuraEngine"
    private var nativeHandle: Long = 0L

    init {
        try {
            System.loadLibrary("aura_runtime")
            Log.i(TAG, "Native library libaura_runtime.so loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library libaura_runtime: ${e.message}")
        }
    }

    @Synchronized
    fun initialize(storagePath: String? = null): Boolean {
        if (nativeHandle != 0L) return true
        return try {
            nativeHandle = if (storagePath != null) {
                // Persistent mode: conversations survive app restarts (SQLite).
                nativeInitWithPath(storagePath)
            } else {
                nativeInit()
            }
            val ok = nativeHandle != 0L
            Log.i(TAG, "AuraEngine initialized with handle: $nativeHandle (success=$ok, persistent=${storagePath != null})")
            ok
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize AuraEngine: ${e.message}")
            false
        }
    }

    @Synchronized
    fun processMessage(input: String): String {
        if (nativeHandle == 0L) {
            if (!initialize()) {
                return "Error: AuraEngine is not initialized"
            }
        }
        return try {
            nativeProcessMessage(nativeHandle, input)
        } catch (e: Throwable) {
            "Error invoking native engine: ${e.message}"
        }
    }

    @Synchronized
    fun emergencyStop() {
        if (nativeHandle != 0L) {
            nativeEmergencyStop(nativeHandle)
        }
    }

    /**
     * Load a GGUF model from a user-selected local file path (no automatic
     * network download). Blocking — call from a background coroutine/thread.
     * Returns a JSON string: {"ok":true, name, contextLength, quantization,
     * sizeBytes} or {"ok":false,"error":…}.
     */
    @Synchronized
    fun loadGguf(path: String): String {
        if (nativeHandle == 0L && !initialize(null)) {
            return """{"ok":false,"error":"engine not initialized"}"""
        }
        return try {
            nativeLoadGguf(nativeHandle, path)
        } catch (e: Throwable) {
            """{"ok":false,"error":"${e.message ?: "unknown JNI error"}"}"""
        }
    }

    /**
     * Chat history of the latest persisted conversation. JSON object:
     * {"conversationCount":N,"messages":[{"role","content","timestamp"}]}.
     * Blocking — call from a background coroutine/thread.
     */
    @Synchronized
    fun getHistoryJson(): String {
        if (nativeHandle == 0L && !initialize(null)) {
            return """{"conversationCount":0,"messages":[],"error":"engine not initialized"}"""
        }
        return try {
            nativeGetHistory(nativeHandle)
        } catch (e: Throwable) {
            """{"conversationCount":0,"messages":[],"error":"${e.message ?: "JNI error"}"}"""
        }
    }

    /**
     * Start a new conversation: wipes stored chat history and resets the agent
     * loop. Memories and audit log are preserved. Blocking.
     * Returns {"ok":bool, "error":…}.
     */
    @Synchronized
    fun newConversation(): String {
        if (nativeHandle == 0L && !initialize(null)) {
            return """{"ok":false,"error":"engine not initialized"}"""
        }
        return try {
            nativeNewConversation(nativeHandle)
        } catch (e: Throwable) {
            """{"ok":false,"error":"${e.message ?: "JNI error"}"}"""
        }
    }

    /**
     * Engine status snapshot. {"agentStatus","modelLoaded","maxIterations",
     * "conversationCount","error"?}. Blocking.
     */
    @Synchronized
    fun getStatusJson(): String {
        if (nativeHandle == 0L && !initialize(null)) {
            return """{"agentStatus":"paused","modelLoaded":false,"maxIterations":20,"conversationCount":0,"error":"engine not initialized"}"""
        }
        return try {
            nativeGetStatus(nativeHandle)
        } catch (e: Throwable) {
            """{"agentStatus":"paused","modelLoaded":false,"maxIterations":20,"conversationCount":0,"error":"${e.message ?: "JNI error"}"}"""
        }
    }

    /**
     * Long-term memory entries, newest first. JSON array of objects
     * {"id","content","entryType","timestamp","tags"}. Blocking.
     */
    @Synchronized
    fun getMemoriesJson(limit: Int = 100): String {
        if (nativeHandle == 0L && !initialize(null)) {
            return "[]"
        }
        return try {
            nativeGetMemories(nativeHandle, limit)
        } catch (e: Throwable) {
            "[]"
        }
    }

    /** Store a long-term memory typed by the user. Blocking. {"ok", "error"?}. */
    @Synchronized
    fun saveMemory(content: String): String {
        if (nativeHandle == 0L && !initialize(null)) {
            return """{"ok":false,"error":"engine not initialized"}"""
        }
        return try {
            nativeSaveMemory(nativeHandle, content)
        } catch (e: Throwable) {
            """{"ok":false,"error":"${e.message ?: "JNI error"}"}"""
        }
    }

    /** Delete all long-term memory entries. Blocking. {"ok", "error"?}. */
    @Synchronized
    fun wipeMemories(): String {
        if (nativeHandle == 0L && !initialize(null)) {
            return """{"ok":false,"error":"engine not initialized"}"""
        }
        return try {
            nativeWipeMemories(nativeHandle)
        } catch (e: Throwable) {
            """{"ok":false,"error":"${e.message ?: "JNI error"}"}"""
        }
    }

    /**
     * Latest audit-log records (real tool actions, model loads, permission
     * changes). JSON array of {"timestamp","event","detail"}. Blocking.
     */
    @Synchronized
    fun getAuditJson(limit: Int = 50): String {
        if (nativeHandle == 0L && !initialize(null)) {
            return "[]"
        }
        return try {
            nativeGetAudit(nativeHandle, limit)
        } catch (e: Throwable) {
            "[]"
        }
    }

    /** Clamp and set the agent's planning-iteration budget. Blocking. */
    @Synchronized
    fun setMaxIterations(n: Int) {
        if (nativeHandle == 0L && !initialize(null)) return
        try {
            nativeSetMaxIterations(nativeHandle, n)
        } catch (_: Throwable) {
            //(settings clamp server-side of a well-defined range)
        }
    }

    @Synchronized
    fun destroy() {
        if (nativeHandle != 0L) {
            nativeDestroy(nativeHandle)
            nativeHandle = 0L
        }
    }

    fun isLoaded(): Boolean = nativeHandle != 0L

    // Native JNI declarations matching Java_app_aura_core_AuraEngine_*
    private external fun nativeInit(): Long
    private external fun nativeInitWithPath(storagePath: String): Long
    private external fun nativeProcessMessage(handle: Long, input: String): String
    private external fun nativeLoadGguf(handle: Long, path: String): String
    private external fun nativeGetHistory(handle: Long): String
    private external fun nativeNewConversation(handle: Long): String
    private external fun nativeGetStatus(handle: Long): String
    private external fun nativeGetMemories(handle: Long, limit: Int): String
    private external fun nativeSaveMemory(handle: Long, content: String): String
    private external fun nativeWipeMemories(handle: Long): String
    private external fun nativeGetAudit(handle: Long, limit: Int): String
    private external fun nativeSetMaxIterations(handle: Long, iterations: Int)
    private external fun nativeEmergencyStop(handle: Long)
    private external fun nativeDestroy(handle: Long)
}
