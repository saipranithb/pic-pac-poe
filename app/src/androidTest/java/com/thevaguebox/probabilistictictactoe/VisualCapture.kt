package com.thevaguebox.probabilistictictactoe

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertTrue

internal fun visualCaptureRequested(): Boolean =
    InstrumentationRegistry.getArguments().getString("captureFormScreenshots") == "true"

/** Compose idleness alone does not settle Window/Dialog transitions or system-bar appearance. */
internal fun captureSettledDevice(name: String) {
    if (!visualCaptureRequested()) return
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    instrumentation.waitForIdleSync()
    SystemClock.sleep(350)
    instrumentation.waitForIdleSync()
    val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "form-verification").apply { mkdirs() }
    val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot()) { "Device screenshot unavailable for $name" }
    try {
        File(directory, "$name.png").outputStream().use { output ->
            assertTrue("PNG encoding failed for $name", bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    } finally {
        bitmap.recycle()
    }
}
