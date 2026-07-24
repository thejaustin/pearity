package com.thejaustin.pearity.shizuku

import java.io.DataOutputStream

/**
 * Helper for executing commands as root (su).
 */
object RootHelper {

    // Probing for su spawns a process, so cache the answer; refreshAvailability()
    // re-probes when the user taps Refresh or changes connection mode.
    @Volatile
    private var cachedAvailable: Boolean? = null

    /** True if 'su' is available in the PATH (cached after first probe) */
    val isAvailable: Boolean
        get() = cachedAvailable ?: refreshAvailability()

    /** Re-probe for su. Blocking — call from Dispatchers.IO. */
    fun refreshAvailability(): Boolean {
        val result = try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            process.waitFor() == 0
        } catch (_: Exception) {
            false
        }
        cachedAvailable = result
        return result
    }

    /**
     * Execute [command] as root. Blocking — call from Dispatchers.IO.
     */
    fun runCommand(command: String): Result<String> {
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)

            os.writeBytes("$command\n")
            os.writeBytes("exit\n")
            os.flush()

            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val exit = process.waitFor()

            if (exit != 0 && stderr.isNotBlank())
                Result.failure(Exception("Root exit $exit: $stderr"))
            else
                Result.success(stdout.trim())
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            process?.destroy()
        }
    }
}
