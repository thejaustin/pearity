package com.thejaustin.pearity.shizuku

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess

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

    /**
     * Execute [command] in a privileged shell via Shizuku.
     * Returns [Result.success] with trimmed stdout, or [Result.failure] with the error message.
     */
    fun runCommand(command: String): Result<String> {
        if (!isAvailable)    return Result.failure(Exception("Shizuku is not running"))
        if (!hasPermission)  return Result.failure(Exception("Shizuku permission not granted"))

        return try {
            val process: ShizukuRemoteProcess = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            val stdout  = process.inputStream.bufferedReader().readText()
            val stderr  = process.errorStream.bufferedReader().readText()
            val exit    = process.waitFor()

            if (exit != 0 && stderr.isNotBlank())
                Result.failure(Exception("Exit $exit: $stderr"))
            else
                Result.success(stdout.trim())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** ADB-compatible equivalent — run via `rish` if Shizuku is unavailable */
    fun runCommandViaRish(command: String): Result<String> {
        return try {
            val process = Runtime.getRuntime()
                .exec(arrayOf("/data/data/com.termux/files/home/rish", "-c", command))
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val exit   = process.waitFor()

            if (exit != 0 && stderr.isNotBlank())
                Result.failure(Exception("rish exit $exit: $stderr"))
            else
                Result.success(stdout.trim())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
