package org.fossify.phone.helpers

import org.fossify.commons.helpers.TAB_CALL_HISTORY
import org.fossify.commons.helpers.TAB_CONTACTS
import org.fossify.commons.helpers.TAB_FAVORITES

// shared prefs
const val SPEED_DIAL = "speed_dial"
const val REMEMBER_SIM_PREFIX = "remember_sim_"
const val GROUP_SUBSEQUENT_CALLS = "group_subsequent_calls"
const val OPEN_DIAL_PAD_AT_LAUNCH = "open_dial_pad_at_launch"
const val DISABLE_PROXIMITY_SENSOR = "disable_proximity_sensor"
const val DISABLE_SWIPE_TO_ANSWER = "disable_swipe_to_answer"
const val SHOW_TABS = "show_tabs"
const val FAVORITES_CONTACTS_ORDER = "favorites_contacts_order"
const val FAVORITES_CUSTOM_ORDER_SELECTED = "favorites_custom_order_selected"
const val WAS_OVERLAY_SNACKBAR_CONFIRMED = "was_overlay_snackbar_confirmed"
const val DIALPAD_VIBRATION = "dialpad_vibration"
const val DIALPAD_BEEPS = "dialpad_beeps"
const val HIDE_DIALPAD_NUMBERS = "hide_dialpad_numbers"
const val ALWAYS_SHOW_FULLSCREEN = "always_show_fullscreen"
const val CALL_RECORDING_ENABLED = "call_recording_enabled"
const val CALL_RECORDING_MODE = "call_recording_mode"
const val CALL_RECORDING_AUDIO_SOURCE = "call_recording_audio_source"
const val CALL_RECORDING_LAST_WORKING_SOURCE = "call_recording_last_working_source"

// call recording modes
const val RECORDING_MODE_MANUAL = 0
const val RECORDING_MODE_ALL = 1
const val RECORDING_MODE_INCOMING = 2
const val RECORDING_MODE_OUTGOING = 3

// call recording audio sources
const val RECORDING_SOURCE_AUTO = 0
const val RECORDING_SOURCE_VOICE_CALL = 1
const val RECORDING_SOURCE_VOICE_COMMUNICATION = 2
const val RECORDING_SOURCE_MIC = 3

const val RECORDING_NOTIFICATION_ID = 43
const val RECORDING_CHANNEL_ID = "simple_dialer_call_recording"

const val ALL_TABS_MASK = TAB_CONTACTS or TAB_FAVORITES or TAB_CALL_HISTORY

val tabsList = arrayListOf(TAB_CONTACTS, TAB_FAVORITES, TAB_CALL_HISTORY)

private const val PATH = "org.fossify.phone.action."
const val ACCEPT_CALL = PATH + "ACCEPT_CALL"
const val DECLINE_CALL = PATH + "DECLINE_CALL"
const val STOP_RECORDING = PATH + "STOP_RECORDING"

const val DIALPAD_TONE_LENGTH_MS = 150L // The length of DTMF tones in milliseconds
