package org.fossify.phone.helpers

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import org.fossify.phone.extensions.config
import org.fossify.phone.models.Events
import org.greenrobot.eventbus.EventBus
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Records the currently active call.
 *
 * Audio sources are tried in order of quality: VOICE_CALL (both sides, needs a device that allows it
 * for dialers), then VOICE_COMMUNICATION, then MIC (always works, but only captures this side clearly).
 * On devices where the OEM blocks VOICE_CALL the recording falls back automatically.
 */
object CallRecorder {
    private var recorder: MediaRecorder? = null
    private var tempFile: File? = null
    private var startedAt = 0L
    private var callNumber = ""
    private var usedSource = 0

    var stateListener: ((Boolean) -> Unit)? = null

    val isRecording: Boolean get() = recorder != null
    fun getStartedAt() = startedAt
    fun getElapsedSeconds() = if (isRecording) (SystemClock.elapsedRealtime() - startedAt) / 1000L else 0L
    fun getCallNumber() = callNumber

    @SuppressLint("MissingPermission")
    fun start(context: Context, number: String, isIncoming: Boolean): Boolean {
        if (isRecording) {
            return true
        }

        val file = createTempFile(context) ?: return false
        val sources = getAudioSources(context)
        for (source in sources) {
            try {
                val newRecorder = createRecorder(context, source, file)
                recorder = newRecorder
                usedSource = source
                tempFile = file
                startedAt = SystemClock.elapsedRealtime()
                callNumber = number
                EventBus.getDefault().post(Events.RecordingStateChanged(true))
                stateListener?.invoke(true)
                return true
            } catch (e: Exception) {
                recorder?.release()
                recorder = null
                file.delete()
            }
        }

        tempFile = null
        return false
    }

    fun stop(context: Context): Uri? {
        val activeRecorder = recorder ?: return null
        recorder = null

        var saved: Uri? = null
        try {
            activeRecorder.stop()
        } catch (e: Exception) {
            // recording shorter than a second or interrupted by the system
        } finally {
            try {
                activeRecorder.release()
            } catch (ignored: Exception) {
            }
        }

        val file = tempFile
        tempFile = null
        if (file != null && file.exists() && file.length() > 0) {
            val displayName = buildDisplayName(callNumber)
            saved = RecordingStorage.publish(context, file, displayName)
        } else {
            file?.delete()
        }

        startedAt = 0L
        callNumber = ""
        EventBus.getDefault().post(Events.RecordingStateChanged(false))
        stateListener?.invoke(false)
        return saved
    }

    fun toggle(context: Context, number: String, isIncoming: Boolean): Boolean {
        return if (isRecording) {
            stop(context)
            false
        } else {
            start(context, number, isIncoming)
        }
    }

    fun getAudioSourceLabel(context: Context) = when (usedSource) {
        MediaRecorder.AudioSource.VOICE_CALL -> "VOICE_CALL"
        MediaRecorder.AudioSource.VOICE_COMMUNICATION -> "VOICE_COMMUNICATION"
        MediaRecorder.AudioSource.MIC -> "MIC"
        else -> ""
    }

    private fun getAudioSources(context: Context): List<Int> {
        val forced = when (context.config.callRecordingAudioSource) {
            RECORDING_SOURCE_VOICE_CALL -> MediaRecorder.AudioSource.VOICE_CALL
            RECORDING_SOURCE_VOICE_COMMUNICATION -> MediaRecorder.AudioSource.VOICE_COMMUNICATION
            RECORDING_SOURCE_MIC -> MediaRecorder.AudioSource.MIC
            else -> null
        }

        val autoSources = listOf(
            MediaRecorder.AudioSource.VOICE_CALL,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.MIC
        )

        return if (forced == null) autoSources else listOf(forced) + autoSources.filter { it != forced }
    }

    private fun createTempFile(context: Context): File? {
        return try {
            File.createTempFile("call_", ".m4a", context.cacheDir)
        } catch (e: Exception) {
            null
        }
    }

    private fun createRecorder(context: Context, source: Int, file: File): MediaRecorder {
        val newRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        newRecorder.apply {
            setAudioSource(source)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44100)
            setAudioEncodingBitRate(96000)
            setAudioChannels(1)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        return newRecorder
    }

    private fun buildDisplayName(number: String): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val safeNumber = number.filter { it.isLetterOrDigit() || it == '+' }.take(24)
        val suffix = if (safeNumber.isEmpty()) "" else "_$safeNumber"
        return "Call_${timestamp}$suffix.m4a"
    }
}
