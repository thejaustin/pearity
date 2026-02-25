package com.thejaustin.pearity.utils

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Date
import kotlin.system.exitProcess

class CrashHandler(private val context: Context) : Thread.UncaughtExceptionHandler {

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    companion object {
        private const val TAG = "CrashHandler"
        private const val CRASH_LOG_FILE = "latest_crash.log"

        fun setup(context: Context) {
            Thread.setDefaultUncaughtExceptionHandler(CrashHandler(context))
        }

        fun getCrashLog(context: Context): String? {
            val file = File(context.filesDir, CRASH_LOG_FILE)
            return if (file.exists()) file.readText() else null
        }

        fun clearCrashLog(context: Context) {
            val file = File(context.filesDir, CRASH_LOG_FILE)
            if (file.exists()) file.delete()
        }
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        val stackTrace = StringWriter()
        throwable.printStackTrace(PrintWriter(stackTrace))
        
        val report = buildString {
            append("--- Pearity Crash Report ---
")
            append("Timestamp: ${Date()}
")
            append("Device: ${Build.MANUFACTURER} ${Build.MODEL}
")
            append("Android Version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})
")
            append("Thread: ${thread.name}
")
            append("
Stack Trace:
")
            append(stackTrace.toString())
            append("
---------------------------
")
        }

        Log.e(TAG, "Uncaught Exception detected!")
        Log.e(TAG, report)

        try {
            val file = File(context.filesDir, CRASH_LOG_FILE)
            file.writeText(report)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save crash log: ${e.message}")
        }

        if (defaultHandler != null) {
            defaultHandler.uncaughtException(thread, throwable)
        } else {
            exitProcess(1)
        }
    }
}
