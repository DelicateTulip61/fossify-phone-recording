package org.fossify.phone.services

import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import org.fossify.commons.extensions.canUseFullScreenIntent
import org.fossify.commons.extensions.hasPermission
import org.fossify.commons.helpers.PERMISSION_POST_NOTIFICATIONS
import org.fossify.phone.R
import org.fossify.phone.activities.CallActivity
import org.fossify.phone.extensions.config
import org.fossify.phone.extensions.isOutgoing
import org.fossify.phone.extensions.keyguardManager
import org.fossify.phone.extensions.powerManager
import org.fossify.phone.helpers.CallManager
import org.fossify.phone.helpers.CallNotificationManager
import org.fossify.phone.helpers.CallRecorder
import org.fossify.phone.helpers.NoCall
import org.fossify.phone.helpers.RECORDING_MODE_ALL
import org.fossify.phone.helpers.RECORDING_MODE_INCOMING
import org.fossify.phone.helpers.RECORDING_MODE_OUTGOING
import org.fossify.phone.helpers.RECORDING_NOTIFICATION_ID
import org.fossify.phone.models.Events
import org.greenrobot.eventbus.EventBus

class CallService : InCallService() {
    private val callNotificationManager by lazy { CallNotificationManager(this) }

    override fun onCreate() {
        super.onCreate()
        CallRecorder.stateListener = { isRecording ->
            if (isRecording) {
                showRecordingForegroundNotification()
            } else {
                hideRecordingForegroundNotification()
            }
        }
    }

    private val callListener = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            if (state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING) {
                callNotificationManager.cancelNotification()
            } else {
                callNotificationManager.setupNotification()
            }
            handleAutoRecording(call, state)
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.onCallAdded(call)
        CallManager.inCallService = this
        call.registerCallback(callListener)

        // Incoming/Outgoing (locked): high priority (FSI)
        // Incoming (unlocked): if user opted in, low priority ➜ manual activity start, otherwise high priority (FSI)
        // Outgoing (unlocked): low priority ➜ manual activity start
        val isIncoming = !call.isOutgoing()
        val isDeviceLocked = !powerManager.isInteractive || keyguardManager.isDeviceLocked
        val lowPriority = when {
            isIncoming && isDeviceLocked -> false
            !isIncoming && isDeviceLocked -> false
            isIncoming && !isDeviceLocked -> config.alwaysShowFullscreen
            else -> true
        }

        callNotificationManager.setupNotification(lowPriority)
        if (
            lowPriority
            || !hasPermission(PERMISSION_POST_NOTIFICATIONS)
            || !canUseFullScreenIntent()
        ) {
            try {
                startActivity(CallActivity.getStartIntent(this))
            } catch (_: Exception) {
                // seems like startActivity can throw AndroidRuntimeException and
                // ActivityNotFoundException, not yet sure when and why, lets show a notification
                callNotificationManager.setupNotification()
            }
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callListener)
        val wasPrimaryCall = call == CallManager.getPrimaryCall()
        CallManager.onCallRemoved(call)
        if (CallManager.getPhoneState() == NoCall) {
            CallManager.inCallService = null
            callNotificationManager.cancelNotification()
        } else {
            callNotificationManager.setupNotification()
            if (wasPrimaryCall) {
                startActivity(CallActivity.getStartIntent(this))
            }
        }

        EventBus.getDefault().post(Events.RefreshCallLog)
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        if (audioState != null) {
            CallManager.onAudioStateChanged(audioState)
        }
    }

    private fun handleAutoRecording(call: Call, state: Int) {
        if (state == Call.STATE_ACTIVE) {
            if (!config.callRecordingEnabled || CallRecorder.isRecording) {
                return
            }

            val isIncoming = !call.isOutgoing()
            val shouldRecord = when (config.callRecordingMode) {
                RECORDING_MODE_ALL -> true
                RECORDING_MODE_INCOMING -> isIncoming
                RECORDING_MODE_OUTGOING -> !isIncoming
                else -> false
            }

            if (shouldRecord) {
                val number = call.details?.handle?.schemeSpecificPart.orEmpty()
                CallRecorder.start(this, number, isIncoming)
            }
        } else if (state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING) {
            if (CallRecorder.isRecording && CallManager.getPhoneState() == NoCall) {
                CallRecorder.stop(this)
            }
        }
    }

    private fun showRecordingForegroundNotification() {
        val number = CallManager.getPrimaryCall()?.details?.handle?.schemeSpecificPart.orEmpty()
        val callerName = number.ifEmpty { getString(R.string.unknown_caller) }
        try {
            startForeground(RECORDING_NOTIFICATION_ID, callNotificationManager.buildRecordingNotification(callerName))
        } catch (ignored: Exception) {
            // recording keeps working, just without the foreground notification
        }
    }

    private fun hideRecordingForegroundNotification() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_DETACH)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(false)
            }
        } catch (ignored: Exception) {
        }
        callNotificationManager.cancelRecordingNotification()
    }

    override fun onDestroy() {
        super.onDestroy()
        CallRecorder.stateListener = null
        if (CallRecorder.isRecording) {
            CallRecorder.stop(this)
        }
        callNotificationManager.cancelNotification()
        callNotificationManager.cancelRecordingNotification()
    }
}
