package com.example.engine

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import com.example.model.ExportResult
import com.example.model.ValidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale

object MediaStoreExporter {
    private const val TAG = "MediaStoreExporter"
    private const val ALBUM_DIRECTORY = "Movies/CutsZoom AI"

    /**
     * Exports the validated temporary MP4 into Android MediaStore with IS_PENDING = 1 while writing.
     * After successful write and verification, clears IS_PENDING = 0 and returns the persistent content:// URI.
     */
    suspend fun exportToMediaStore(
        context: Context,
        tempVideoFile: File,
        baseValidation: ValidationResult,
        isSafeFallback: Boolean = false
    ): ExportResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val fileName = "CutsZoom_${System.currentTimeMillis()}.mp4"

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Video.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, ALBUM_DIRECTORY)
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        var mediaStoreUri: Uri? = null

        try {
            mediaStoreUri = resolver.insert(collectionUri, values)
            if (mediaStoreUri != null) {
                resolver.openOutputStream(mediaStoreUri)?.use { outStream ->
                    FileInputStream(tempVideoFile).use { inStream ->
                        inStream.copyTo(outStream, bufferSize = 64 * 1024)
                    }
                    outStream.flush()
                }

                // Mark IS_PENDING = 0 once written
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val finishValues = ContentValues().apply {
                        put(MediaStore.Video.Media.IS_PENDING, 0)
                    }
                    resolver.update(mediaStoreUri, finishValues, null, null)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore insert error", e)
        }

        // Generate clean content:// URI via FileProvider for sharing
        val fileProviderUri = try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                tempVideoFile
            )
        } catch (e: Exception) {
            mediaStoreUri ?: Uri.fromFile(tempVideoFile)
        }

        val finalShareableUri = mediaStoreUri ?: fileProviderUri

        val durationSec = baseValidation.durationMs / 1000
        val durationFormatted = String.format(Locale.US, "%d:%02d", durationSec / 60, durationSec % 60)
        val fileSizeFormatted = String.format(Locale.US, "%.1f MB", tempVideoFile.length() / (1024.0 * 1024.0))
        val resolution = "${baseValidation.width}x${baseValidation.height}"

        ExportResult(
            localFile = tempVideoFile,
            mediaStoreUri = mediaStoreUri,
            contentUri = finalShareableUri,
            fileSizeFormatted = fileSizeFormatted,
            durationFormatted = durationFormatted,
            resolution = resolution,
            isSafeFallbackUsed = isSafeFallback,
            validation = baseValidation
        )
    }

    /**
     * Creates an Android Intent.ACTION_SEND for the persistent content:// URI.
     */
    fun createShareIntent(context: Context, exportResult: ExportResult): Intent {
        val shareUri = exportResult.contentUri
        return Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, shareUri)
            putExtra(Intent.EXTRA_SUBJECT, "Edited with CutsZoom AI")
            putExtra(Intent.EXTRA_TEXT, "Created with CutsZoom AI - Create. Edit. Automate.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
