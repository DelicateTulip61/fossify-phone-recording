package org.fossify.phone.activities

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.extensions.beGoneIf
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.phone.R
import org.fossify.phone.adapters.RecordingsAdapter
import org.fossify.phone.databinding.ActivityRecordingsBinding
import org.fossify.phone.helpers.RecordingStorage
import org.fossify.phone.models.CallRecording

class RecordingsActivity : SimpleActivity() {
    companion object {
        fun getStartIntent(context: Context): Intent {
            return Intent(context, RecordingsActivity::class.java)
        }
    }

    private val binding by viewBinding(ActivityRecordingsBinding::inflate)
    private var recordings = arrayListOf<CallRecording>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        binding.apply {
            setupEdgeToEdge(padBottomSystem = listOf(recordingsList))
            recordingsList.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@RecordingsActivity)
        }
        updateTextColors(binding.recordingsList)
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.recordingsAppbar, NavigationIcon.Arrow)
        loadRecordings()
    }

    private fun loadRecordings() {
        ensureBackgroundThread {
            val result = RecordingStorage.getAll(this)
            runOnUiThread {
                recordings = result.toMutableList() as ArrayList<CallRecording>
                updateAdapter()
            }
        }
    }

    private fun updateAdapter() {
        binding.recordingsEmptyView.beVisibleIf(recordings.isEmpty())
        binding.recordingsList.beGoneIf(recordings.isEmpty())

        val adapter = RecordingsAdapter(this, recordings, ::playRecording, ::showRecordingOptions)
        binding.recordingsList.adapter = adapter
        binding.recordingsEmptyView.setTextColor(getProperTextColor())
    }

    private fun playRecording(recording: CallRecording) {
        val uri = RecordingStorage.getPlayableUri(this, recording)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "audio/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            toast(R.string.no_app_for_playing_recordings)
        }
    }

    private fun showRecordingOptions(recording: CallRecording) {
        val options = arrayListOf(
            getString(R.string.play_recording),
            getString(R.string.share_recording),
            getString(R.string.delete_recording)
        )

        AlertDialog.Builder(this)
            .setItems(options.toTypedArray()) { _, which ->
                when (which) {
                    0 -> playRecording(recording)
                    1 -> shareRecording(recording)
                    2 -> confirmDelete(recording)
                }
            }
            .show()
    }

    private fun shareRecording(recording: CallRecording) {
        val uri = RecordingStorage.getPlayableUri(this, recording)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_recording)))
    }

    private fun confirmDelete(recording: CallRecording) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_recording)
            .setMessage(R.string.delete_recording_confirmation)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                deleteRecording(recording)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun deleteRecording(recording: CallRecording) {
        ensureBackgroundThread {
            val deleted = RecordingStorage.delete(this, recording)
            runOnUiThread {
                if (deleted) {
                    recordings.remove(recording)
                    updateAdapter()
                } else {
                    toast(R.string.deleting_failed)
                }
            }
        }
    }
}
