package org.fossify.phone.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.extensions.formatDate
import org.fossify.commons.extensions.formatSize
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.setupViewBackground
import org.fossify.phone.R
import org.fossify.phone.models.CallRecording
import org.fossify.phone.views.RecordingViewHolder

class RecordingsAdapter(
    private val activity: org.fossify.phone.activities.RecordingsActivity,
    private val recordings: List<CallRecording>,
    private val onClick: (CallRecording) -> Unit,
    private val onLongClick: (CallRecording) -> Unit
) : RecyclerView.Adapter<RecordingViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordingViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_recording, parent, false)
        return RecordingViewHolder(view)
    }

    override fun getItemCount() = recordings.size

    override fun onBindViewHolder(holder: RecordingViewHolder, position: Int) {
        val recording = recordings[position]
        holder.apply {
            itemView.setupViewBackground(activity)
            recordingName.text = recording.displayName
            recordingName.setTextColor(activity.getProperTextColor())

            val date = recording.dateAdded.formatDate(activity)
            val duration = formatDuration(recording.durationMs)
            val size = recording.size.formatSize()
            recordingDetails.text = "$date • $duration • $size"

            itemView.setOnClickListener { onClick(recording) }
            itemView.setOnLongClickListener {
                onLongClick(recording)
                true
            }
        }
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }
}
