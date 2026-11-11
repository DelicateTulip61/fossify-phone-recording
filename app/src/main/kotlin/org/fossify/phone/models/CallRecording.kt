package org.fossify.phone.models

import android.net.Uri

data class CallRecording(
    val uri: Uri,
    val displayName: String,
    val dateAdded: Long,
    val size: Long,
    val durationMs: Long
)
