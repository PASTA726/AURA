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
    private external fun nativeEmergencyStop(handle: Long)
    private external fun nativeDestroy(handle: Long)
}
