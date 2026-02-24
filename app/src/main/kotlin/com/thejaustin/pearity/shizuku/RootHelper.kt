package com.thejaustin.pearity.shizuku

import java.io.DataOutputStream

/**
 * Helper for executing commands as root (su).
 */
object RootHelper {

    /** True if 'su' is available in the PATH */
    val isAvailable: Boolean
        get() = try {
            val process = Runtime.getRuntime().exec("which su")
            process.waitFor() == 0
        } catch (_: Exception) {
            false
        }

    /**
     * Execute [command] as root.
     */
    fun runCommand(command: String): Result<String> {
        return try {
            val process = Runtime.getRuntime().exec("su")
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
            // Cleanup process if needed
        }
    }
}
