# Phone — call recording fork

![Icon](graphics/fork_icon_preview.png)

A fork of [Fossify Phone](https://github.com/FossifyOrg/Phone) with built-in call recording.

Why: the Google dialer has no recording button on most devices, and neither has the stock Fossify build. This fork adds it without ads, trackers or a Google account.

## Features

- **Record from the in-call screen** — a record button next to mute/speaker/dialpad.
- **Automatic recording** — off, all calls, incoming only, or outgoing only.
- **Selectable audio source** — automatic, voice call (both sides), voice communication, or microphone only.
- **Recordings manager** — play, share and delete saved calls.
- **Ongoing notification** with a stop action while a call is being recorded.
- Recordings are stored as AAC/M4A in `Music/Call recordings`, readable by any file manager or player.
- Everything else is upstream Fossify Phone: T9 dialer, call log, blocking, multi-SIM, no ads.

## Install

Download the APK from this repository's releases and sideload it, then set the app as the default Phone app.

The app id is `org.fossify.phone.rec`, so it can be installed next to both Google Phone and Fossify Phone.

## Recording quality and device support

Android has no public, guaranteed API for recording both call sides. The app tries `VOICE_CALL` first, then `VOICE_COMMUNICATION`, then the microphone. On devices and ROMs that allow `VOICE_CALL` for the default dialer you get a clean two-sided recording. On locked-down ROMs the fallback records your side plus room audio through the mic — use the speaker for the best result.

If a source fails, the app falls back automatically and keeps recording instead of aborting.

## Build

Requirements: JDK 17+ and Android SDK 36.

```bash
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew assembleFossDebug
```

The APK lands in `app/build/outputs/apk/foss/debug/`.

The upstream `foss` and `gplay` flavors are both still available; `foss` has no Google dependencies.

## Changes from upstream

- `CallRecorder`, `RecordingStorage`, recording settings and the recordings screen.
- `CallService` starts/stops automatic recordings and holds the foreground recording notification.
- New `RECORD_AUDIO` runtime prompt, plus `FOREGROUND_SERVICE_MICROPHONE`, `FOREGROUND_SERVICE_PHONE_CALL` and legacy `WRITE_EXTERNAL_STORAGE` (`maxSdkVersion=28`).
- App id `org.fossify.phone.rec`, version `1.11.2-rec`.
- Launcher name and icon styled after the Google dialer.

## License

GPL-3.0, same as upstream. Original code © Fossify contributors.
