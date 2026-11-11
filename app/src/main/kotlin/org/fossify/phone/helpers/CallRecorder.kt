package org.fossify.phone.helpers

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
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
 * Android guarantees no source that captures both sides, and some ROMs accept VOICE_CALL but then
 * write pure silence. So every candidate is started, sampled for 3 seconds through
 * MediaRecorder.getMaxAmplitude(), and swapped for the next one when it stays at zero.
 * The last source that produced real audio is remembered and tried first next time.
 */
object CallRecorder {
    private const val TAG = "CallRecorder"
    private const val SILENCE_CHECK_DELAY = 3000L

    private var recorder: MediaRecorder? = null
    private var tempFile: File? = null
    private var startedAt = 0L
    private var callNumber = ""
    private var usedSource = 0
    private var attemptId = 0
    private var candidates: List<Int> = emptyList()
    private var candidateIndex = 0
    private var hasSentStartEvent = false

    private val handler = Handler(Looper.getMainLooper())

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

        callNumber = number
        candidates = getAudioSources(context)
        candidateIndex = 0
        hasSentStartEvent = false

        val started = tryNextSource(context)
        if (started) {
            sendStartEventIfNeeded()
        }
        return started
    }

    private fun tryNextSource(context: Context): Boolean {
        while (candidateIndex < candidates.size) {
            val source = candidates[candidateIndex]
            val file = createTempFile(context)
            if (file == null) {
                candidateIndex++
                continue
            }

            try {
                val newRecorder = createRecorder(context, source, file)
                recorder = newRecorder
                tempFile = file
                startedAt = SystemClock.elapsedRealtime()
                usedSource = source
                Log.d(TAG, "recording started with source $source")
                scheduleSilenceCheck(context)
                return true
            } catch (e: Exception) {
                Log.w(TAG, "source $source failed: ${e.message}")
                try {
                    recorder?.release()
                } catch (ignored: Exception) {
                }
                recorder = null
                file.delete()
                candidateIndex++
            }
        }

        Log.w(TAG, "no usable audio source")
        return false
    }

    private fun scheduleSilenceCheck(context: Context) {
        val currentAttempt = ++attemptId
        handler.postDelayed({
            if (currentAttempt != attemptId || recorder == null) {
                return@postDelayed
            }

            val amplitude = try {
                recorder?.maxAmplitude ?: 0
            } catch (e: Exception) {
                0
            }
            Log.d(TAG, "amplitude after ${SILENCE_CHECK_DELAY}ms: $amplitude (source $usedSource)")

            if (amplitude > 0) {
                context.config.lastWorkingRecordingSource = usedSource
                return@postDelayed
            }

            // the source is accepted but writes silence, move on to the next one
            abortCurrent()
            candidateIndex++
            if (!tryNextSource(context)) {
                stop(context)
                return@postDelayed
            }
            sendStartEventIfNeeded()
        }, SILENCE_CHECK_DELAY)
    }

    private fun sendStartEventIfNeeded() {
        if (hasSentStartEvent) {
            return
        }
        hasSentStartEvent = true
        EventBus.getDefault().post(Events.RecordingStateChanged(true))
        stateListener?.invoke(true)
    }

    private fun abortCurrent() {
        val activeRecorder = recorder ?: return
        recorder = null
        attemptId++

        try {
            activeRecorder.stop()
        } catch (ignored: Exception) {
        }
        try {
            activeRecorder.release()
        } catch (ignored: Exception) {
        }
        tempFile?.delete()
        tempFile = null
    }

    fun stop(context: Context): Uri? {
        val activeRecorder = recorder ?: return null
        recorder = null
        attemptId++

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
        hasSentStartEvent = false
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

    fun getAudioSourceLabel() = when (usedSource) {
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

        return if (forced == null) {
            val lastWorking = context.config.lastWorkingRecordingSource
            val ordered = if (lastWorking != 0 && autoSources.contains(lastWorking)) {
                listOf(lastWorking) + autoSources.filter { it != lastWorking }
            } else {
                autoSources
            }
            // the microphone always stays as the final safety net
            ordered.filter { it != MediaRecorder.AudioSource.MIC } + MediaRecorder.AudioSource.MIC
        } else {
            listOf(forced) + autoSources.filter { it != forced }
        }
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
