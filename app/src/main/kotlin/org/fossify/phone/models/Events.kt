package org.fossify.phone.models

sealed class Events {
    data object RefreshCallLog : Events()
    data class RecordingStateChanged(val isRecording: Boolean) : Events()
}
