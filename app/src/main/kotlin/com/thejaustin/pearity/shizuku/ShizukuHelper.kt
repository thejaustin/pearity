package com.thejaustin.pearity.shizuku

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.File

object ShizukuHelper {

    /** True if the Shizuku daemon is reachable */
    val isAvailable: Boolean
        get() = try {
            Shizuku.pingBinder()
        } catch (_: Exception) {
            false
        } catch (_: NoClassDefFoundError) {
            false
        }

    /** True if we hold the Shizuku API permission */
    val hasPermission: Boolean
        get() = try {
            !Shizuku.isPreV11() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }

    fun requestPermission(requestCode: Int) {
        try {
            if (!Shizuku.isPreV11()) Shizuku.requestPermission(requestCode)
        } catch (_: Exception) { /* ignore */ }
    }

    /** Run [command] as the Shizuku identity (shell uid). Blocking — call from Dispatchers.IO. */
    fun runCommand(command: String): Result<String> {
        if (!isAvailable)   return Result.failure(Exception("Shizuku is not running"))
        if (!hasPermission) return Result.failure(Exception("Shizuku permission not granted"))
        var process: Process? = null
        return try {
            process = newProcess(arrayOf("sh", "-c", command))
            process.outputStream.close() // the command takes no stdin; don't leave the child waiting
            val (stdout, stderr, exit) = process.collectOutput()
            if (exit == 0)
                Result.success(stdout.trim())
            else
                Result.failure(Exception("shizuku($exit): ${stderr.ifBlank { stdout }.trim()}"))
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            process?.destroy()
        }
    }

    // Shizuku.newProcess is private API but the de-facto stable way to spawn a remote
    // shell; proguard-rules.pro keeps all of rikka.shizuku so reflection survives R8.
    private fun newProcess(cmd: Array<String>): Process {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        )
        method.isAccessible = true
        return method.invoke(null, cmd, null, null) as Process
    }

    /** Fallback: run via a rish binary if one is accessible. Blocking — call from Dispatchers.IO. */
    fun runCommandViaRish(command: String): Result<String> = runViaRish(command)

    private fun runViaRish(command: String): Result<String> {
        val rishPaths = listOf(
            "/data/local/tmp/rish",
        )

        for (rishPath in rishPaths) {
            if (!File(rishPath).canExecute()) continue
            var process: Process? = null
            return try {
                process = Runtime.getRuntime()
                    .exec(arrayOf(rishPath, "-c", command))
                process.outputStream.close()
                val (stdout, stderr, exit) = process.collectOutput()

                if (exit != 0 && stderr.isNotBlank())
                    Result.failure(Exception("rish exit $exit: $stderr"))
                else
                    Result.success(stdout.trim())
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                process?.destroy()
            }
        }

        return Result.failure(Exception("rish not accessible — use Shizuku or Root mode instead"))
    }
}
