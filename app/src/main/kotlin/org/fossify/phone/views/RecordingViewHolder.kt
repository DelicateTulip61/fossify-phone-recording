package org.fossify.phone.views

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.views.MyTextView
import org.fossify.phone.R

class RecordingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val recordingName: MyTextView = view.findViewById(R.id.recording_name)
    val recordingDetails: MyTextView = view.findViewById(R.id.recording_details)
}
