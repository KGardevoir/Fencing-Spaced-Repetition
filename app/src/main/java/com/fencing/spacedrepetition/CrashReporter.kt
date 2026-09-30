// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition

import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

/**
 * The application, which exists only to install [CrashReporter] as early as
 * anything can run.
 */
class FencingApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        CrashReporter.install(this)
    }
}

/**
 * Shows an uncaught exception on the device instead of letting the app vanish.
 *
 * Android's own crash dialog offers no stack trace, and without adb there is
 * no other way to see one -- which is how a release build that crashed on
 * launch went undiagnosed. The trace is written to a file and shown by
 * [CrashActivity], which runs in its own process and uses nothing but the
 * framework's widgets: no Compose, no database, nothing of the app that might
 * be what failed.
 */
object CrashReporter {
    private const val CRASH_PROCESS_SUFFIX = ":crash"

    fun install(context: Context) {
        // The crash screen itself must not report into itself.
        if (currentProcessName().endsWith(CRASH_PROCESS_SUFFIX)) return

        val appContext = context.applicationContext ?: context
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val report = report(thread, error)
                crashFile(appContext).writeText(report)
                appContext.startActivity(
                    Intent(appContext, CrashActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )
                Process.killProcess(Process.myPid())
                exitProcess(10)
            } catch (reportFailure: Throwable) {
                previous?.uncaughtException(thread, error)
            }
        }
    }

    internal fun crashFile(context: Context) = File(context.filesDir, "last_crash.txt")

    private fun report(thread: Thread, error: Throwable): String {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }
        return buildString {
            appendLine("App: ${BuildConfig.APPLICATION_ID} ${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TYPE})")
            appendLine("Commit: ${BuildConfig.GIT_COMMIT}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("ABIs: ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("Thread: ${thread.name}")
            appendLine()
            append(trace)
        }
    }

    private fun currentProcessName(): String =
        if (Build.VERSION.SDK_INT >= 28) {
            Application.getProcessName()
        } else {
            runCatching { File("/proc/self/cmdline").readText().trimEnd('\u0000') }.getOrDefault("")
        }
}

/** Shows the last crash report, with a way to get it off the device. */
class CrashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val report = CrashReporter.crashFile(this).takeIf { it.exists() }?.readText()
            ?: "No crash report was found."

        val text = TextView(this).apply {
            this.text = report
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextIsSelectable(true)
            setPadding(24, 24, 24, 24)
        }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(Button(this@CrashActivity).apply {
                this.text = "Copy"
                setOnClickListener {
                    getSystemService(ClipboardManager::class.java)
                        .setPrimaryClip(ClipData.newPlainText("Crash report", report))
                }
            })
            addView(Button(this@CrashActivity).apply {
                this.text = "Share"
                setOnClickListener {
                    startActivity(Intent.createChooser(
                        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report),
                        "Share crash report"
                    ))
                }
            })
            addView(Button(this@CrashActivity).apply {
                this.text = "Restart"
                setOnClickListener {
                    startActivity(Intent(this@CrashActivity, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                    finish()
                }
            })
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            addView(TextView(this@CrashActivity).apply {
                this.text = "The app crashed"
                textSize = 20f
                setPadding(24, 24, 24, 0)
            })
            addView(buttons)
            addView(ScrollView(this@CrashActivity).apply {
                addView(HorizontalScrollView(this@CrashActivity).apply { addView(text) })
            })
        })
    }
}
