package com.sandbox.ball.core.container

import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

object NativeLibExtractor {
    private const val TAG = "NativeLibExtractor"

    fun extractNativeLibs(apkFile: File, targetLibDir: File) {
        if (!apkFile.exists()) return
        if (!targetLibDir.exists()) targetLibDir.mkdirs()

        try {
            ZipFile(apkFile).use { zip ->
                val entries = zip.entries().toList()
                val supportedAbis = Build.SUPPORTED_ABIS

                // Find the best matching ABI present in the APK
                var selectedAbi: String? = null
                for (abi in supportedAbis) {
                    val hasAbi = entries.any { it.name.startsWith("lib/$abi/") && it.name.endsWith(".so") }
                    if (hasAbi) {
                        selectedAbi = abi
                        break
                    }
                }

                if (selectedAbi == null) {
                    Log.i(TAG, "No native libraries found for supported ABIs in ${apkFile.name}")
                    return
                }

                Log.i(TAG, "Extracting native libraries for ABI: $selectedAbi to ${targetLibDir.absolutePath}")
                val prefix = "lib/$selectedAbi/"

                for (entry in entries) {
                    if (entry.name.startsWith(prefix) && entry.name.endsWith(".so")) {
                        val soName = entry.name.substring(prefix.length)
                        val outFile = File(targetLibDir, soName)

                        // Only extract if doesn't exist or size mismatch
                        if (!outFile.exists() || outFile.length() != entry.size) {
                            zip.getInputStream(entry).use { inStream ->
                                FileOutputStream(outFile).use { outStream ->
                                    inStream.copyTo(outStream)
                                }
                            }
                            Log.d(TAG, "Extracted native lib: $soName (${outFile.length()} bytes)")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed extracting native libraries from ${apkFile.name}: ${e.message}", e)
        }
    }
}
