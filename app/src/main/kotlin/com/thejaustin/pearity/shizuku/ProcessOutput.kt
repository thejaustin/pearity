package com.thejaustin.pearity.shizuku

/** stdout, stderr, and exit code of a finished process. */
internal data class ProcessOutput(val stdout: String, val stderr: String, val exit: Int)

/**
 * Drain stdout and stderr concurrently, then wait for exit.
 *
 * Reading the two streams sequentially can deadlock: if the child fills the
 * un-drained pipe's ~64KB buffer it blocks on write while we block on read.
 * Harmless for tiny `settings get` output, but a trap for anything bigger.
 */
internal fun Process.collectOutput(): ProcessOutput {
    var stderr = ""
    val stderrThread = Thread {
        stderr = try {
            errorStream.bufferedReader().readText()
        } catch (_: Exception) {
            ""
        }
    }.apply { isDaemon = true; start() }

    val stdout = inputStream.bufferedReader().readText()
    stderrThread.join()
    val exit = waitFor()
    return ProcessOutput(stdout, stderr, exit)
}
