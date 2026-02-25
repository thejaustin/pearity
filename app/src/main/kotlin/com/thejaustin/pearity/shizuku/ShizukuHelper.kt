package com.thejaustin.pearity.shizuku

import android.content.Intent
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

    /**
     * Execute [command] via the rish shell, which routes through the Shizuku daemon.
     * rish is Shizuku's own privileged shell binary — using it avoids the private
     * Shizuku.newProcess() API while still running with elevated permissions.
     */
    fun runCommand(command: String): Result<String> {
        if (!isAvailable)   return Result.failure(Exception("Shizuku is not running"))
        if (!hasPermission) return Result.failure(Exception("Shizuku permission not granted"))
        val result = runViaRish(command)
        
        // Split-brain prevention for Samsung devices
        if (result.isSuccess && command.contains("settings put")) {
            syncSamsungState()
        }
        
        return result
    }

    /** Fallback: run via rish without checking Shizuku permission state first */
    fun runCommandViaRish(command: String): Result<String> = runViaRish(command)

    private fun runViaRish(command: String): Result<String> {
        val rishPaths = listOf(
            "/data/data/com.termux/files/home/rish",
            "/data/local/tmp/rish",
        )

        for (rishPath in rishPaths) {
            if (!File(rishPath).exists()) continue
            return try {
                val process = Runtime.getRuntime()
                    .exec(arrayOf(rishPath, "-c", command))
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

        return Result.failure(Exception("rish not found — is Shizuku running?"))
    }

    /**
     * Prevents "Split-Brain" on Samsung devices by forcing One UI to reconcile
     * its proprietary database with the standard Android settings provider.
     */
    private fun syncSamsungState() {
        try {
            // Using rish to broadcast configuration change which triggers Samsung observers
            runViaRish("am broadcast -a android.intent.action.CONFIGURATION_CHANGED")
        } catch (e: Exception) {
            // ignore
        }
    }
}
