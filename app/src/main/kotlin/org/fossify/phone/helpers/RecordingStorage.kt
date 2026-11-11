package org.fossify.phone.helpers

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import org.fossify.phone.BuildConfig
import org.fossify.phone.models.CallRecording
import java.io.File

/**
 * Stores call recordings in the public Music/Call recordings folder.
 * On Android 10+ MediaStore is used, so no storage permission is needed.
 * On older versions the file is written directly and then media-scanned.
 */
object RecordingStorage {
    private const val FOLDER_NAME = "Call recordings"
    private const val MIME_TYPE = "audio/mp4"

    fun getRecordingsFolder(): File {
        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        return File(musicDir, FOLDER_NAME)
    }

    fun publish(context: Context, tempFile: File, displayName: String): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                publishToMediaStore(context, tempFile, displayName)
            } else {
                publishToFileSystem(context, tempFile, displayName)
            }
        } catch (e: Exception) {
            null
        } finally {
            tempFile.delete()
        }
    }

    private fun publishToMediaStore(context: Context, tempFile: File, displayName: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Audio.Media.MIME_TYPE, MIME_TYPE)
            put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/$FOLDER_NAME")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }

        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = context.contentResolver.insert(collection, values) ?: return null

        context.contentResolver.openOutputStream(uri)?.use { output ->
            tempFile.inputStream().use { input -> input.copyTo(output) }
        } ?: run {
            context.contentResolver.delete(uri, null, null)
            return null
        }

        values.clear()
        values.put(MediaStore.Audio.Media.IS_PENDING, 0)
        context.contentResolver.update(uri, values, null, null)
        return uri
    }

    @Suppress("DEPRECATION")
    private fun publishToFileSystem(context: Context, tempFile: File, displayName: String): Uri? {
        val folder = getRecordingsFolder()
        if (!folder.exists() && !folder.mkdirs()) {
            return null
        }

        val target = File(folder, displayName)
        tempFile.copyTo(target, overwrite = true)
        android.media.MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(MIME_TYPE), null)
        return Uri.fromFile(target)
    }

    fun getPlayableUri(context: Context, recording: CallRecording): Uri {
        val uri = recording.uri
        return if (uri.scheme == "file") {
            FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", File(uri.path!!))
        } else {
            uri
        }
    }

    fun delete(context: Context, recording: CallRecording): Boolean {
        return try {
            if (recording.uri.scheme == "file") {
                val file = File(recording.uri.path!!)
                val deleted = file.delete()
                if (deleted) {
                    android.media.MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
                }
                deleted
            } else {
                context.contentResolver.delete(recording.uri, null, null) > 0
            }
        } catch (e: Exception) {
            false
        }
    }

    fun getAll(context: Context): List<CallRecording> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            queryMediaStore(context)
        } else {
            queryFileSystem(context)
        }
    }

    private fun queryMediaStore(context: Context): List<CallRecording> {
        val recordings = mutableListOf<CallRecording>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DURATION
        )
        val selection = "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("%$FOLDER_NAME%")

        context.contentResolver.query(
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Audio.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                    id
                )
                recordings.add(
                    CallRecording(
                        uri = uri,
                        displayName = cursor.getString(nameColumn) ?: "",
                        dateAdded = cursor.getLong(dateColumn) * 1000L,
                        size = cursor.getLong(sizeColumn),
                        durationMs = cursor.getLong(durationColumn)
                    )
                )
            }
        }
        return recordings
    }

    @Suppress("DEPRECATION")
    private fun queryFileSystem(context: Context): List<CallRecording> {
        val folder = getRecordingsFolder()
        val files = folder.listFiles { file -> file.isFile } ?: return emptyList()
        return files.sortedByDescending { it.lastModified() }.map { file ->
            CallRecording(
                uri = Uri.fromFile(file),
                displayName = file.name,
                dateAdded = file.lastModified(),
                size = file.length(),
                durationMs = getDuration(file)
            )
        }
    }

    private fun getDuration(file: File): Long {
        return try {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(file.absolutePath)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            }
        } catch (e: Exception) {
            0L
        }
    }
}
