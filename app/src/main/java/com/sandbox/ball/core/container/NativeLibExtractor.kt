package com.sandbox.ball.core.container

import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

object NativeLibExtractor {
    private const val TAG = "NativeLibExtractor"

    /**
     * Validates that an entry inside the ZIP archive does not escape the destination directory.
     * Prevents Zip Slip (Arbitrary File Overwrite) attacks (SEC-01).
     */
    fun validateEntry(entryName: String, prefix: String, targetLibDir: File): File {
        if (!entryName.startsWith(prefix)) {
            throw IllegalArgumentException("Entry '$entryName' does not start with prefix '$prefix'")
        }
        val soName = entryName.substring(prefix.length)
        if (soName.contains("/") || soName.contains("\\") || soName.contains("..")) {
            throw SecurityException("Zip Slip attack detected on entry: $entryName")
        }

        val canonicalDestDir = targetLibDir.canonicalPath + File.separator
        val outFile = File(targetLibDir, soName)
        val canonicalOutPath = outFile.canonicalPath

        if (!canonicalOutPath.startsWith(canonicalDestDir)) {
            throw SecurityException("Zip Slip attack detected on entry: $entryName")
        }

        return outFile
    }

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
                        try {
                            val outFile = validateEntry(entry.name, prefix, targetLibDir)

                            // Only extract if doesn't exist or size mismatch
                            if (!outFile.exists() || outFile.length() != entry.size) {
                                zip.getInputStream(entry).use { inStream ->
                                    FileOutputStream(outFile).use { outStream ->
                                        inStream.copyTo(outStream)
                                    }
                                }
                                Log.d(TAG, "Extracted native lib: ${outFile.name} (${outFile.length()} bytes)")
                            }
                        } catch (se: SecurityException) {
                            Log.w(TAG, "Blocked suspicious zip entry: ${entry.name} - ${se.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed extracting native libraries from ${apkFile.name}: ${e.message}", e)
        }
    }
}
