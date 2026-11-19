# Cabin Sound Studio (`com.oem.audiocontrol`)

**Multi-Zone Car Audio & Cabin Sound Controller for Android Automotive OS (AAOS)**

`com.oem.audiocontrol` is a system-privileged OEM infotainment application built for Android Automotive OS (AAOS 12+ / API 31+).
It demonstrates multi-zone cabin audio routing via `CarAudioManager`, 
priority audio focus arbitration (`AudioFocusRequest` for navigation ducking and ADAS safety chimes),
interactive 2D spatial sound staging, and ECU suspend/resume state persistence via `CarPowerManager`.

---

## Key Capabilities

* **Multi-Zone Cabin Audio Routing (`CarAudioManager`)**: Dynamically discovers vehicle audio zones (`PRIMARY_AUDIO_ZONE` for the front cabin and secondary zones for Rear Seat Entertainment) using `getAudioZoneIds()`, inspects hardware volume groups (`getVolumeGroupCount()`), and resolves mapped `AudioAttributes` usages (`getUsagesForVolumeGroupId()`).
* **Bus-Level Volume & Mute Control**: Adjusts per-group volume levels (`setGroupVolume()`), toggles group mute states (`setVolumeGroupMute()`), and synchronizes UI state in real time with physical steering-wheel buttons and rotary knobs via `CarVolumeCallback`.
* **Priority Audio Focus & Safety Arbitration (`AudioFocusRequest`)**:
    * **Navigation Ducking (`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`)**: Simulates turn-by-turn navigation prompts (`USAGE_ASSISTANCE_NAVIGATION_GUIDANCE`) attenuating primary media playback (`-12 dB`) without interrupting rear zones.
    * **ADAS & Emergency Chime Preemption (`USAGE_SAFETY`)**: Simulates high-priority collision/safety alerts preempting entertainment buses with acoustic feedback via `ToneGenerator`.
* **Interactive 2D Cabin Sound Stage**: Custom Jetpack Compose `Canvas` control allowing tactile Balance (Left/Right) and Fade (Front/Rear) adjustment across the cabin chassis.
* **Vehicle Power Lifecycle Persistence (`CarPowerManager`)**: Listens to ECU power state transitions (`STATE_SHUTDOWN_PREPARE`, `STATE_SUSPEND_ENTER`, `STATE_ON`) via `CarPowerStateListener` to snapshot multi-zone volume levels before Suspend-to-RAM deep sleep and restore them automatically upon ignition wake.

---

## System Architecture

```text
+---------------------------------------------------------------------------------------------------+
|                        PRIVILEGED SYSTEM APP LAYER (/system/priv-app)                             |
|                                   com.oem.audiocontrol                                            |
|                                                                                                   |
|  +---------------------------------------------------------------------------------------------+  |
|  |                        JETPACK COMPOSE UI & SCREEN FLOW LAYER                               |  |
|  |  [ Screen 1: Primary Mixer ] <---> [ Screen 2: Rear Seat Zone ]                             |  |
|  |       |           ^                         |                                               |  |
|  |       v           |                         v                                               |  |
|  |  [ Screen 3: Focus & Chime ]       [ Screen 4: Power Suspend Overlay ]                      |  |
|  +---------------------------------------------------------------------------------------------+  |
|                                       |                    ^                                      |
|                                       v                    |                                      |
|  +---------------------------------------------------------------------------------------------+  |
|  |                           VIEWMODEL & CONTROLLER LAYER                                      |  |
|  |  AudioControlViewModel                                                                      |  |
|  |    ├──► CarAudioController  (Zones, Volume Groups, Mute, DSP Snapshot)                      |  |
|  |    └──► AudioFocusSimulator (Nav Ducking & ADAS Safety Chime Requests)                      |  |
|  +---------------------------------------------------------------------------------------------+  |
+-------------|----------------------------------|----------------------------------|---------------+
              | Binder IPC (android.car.jar)     | Binder IPC                       | Binder IPC
              v                                  v                                  v
+---------------------------------------------------------------------------------------------------+
|                           ANDROID AUTOMOTIVE FRAMEWORK (CarService)                               |
|  [ CarAudioManager / CarAudioService ]   [ AudioManager / CarAudioFocus ]   [ CarPowerManager ]   |
+-------------------|----------------------------------|-----------------------------|--------------+
                    | HIDL / AIDL                      | AudioFlinger                | VHAL IPC
                    v                                  v                             v
+---------------------------------------------------------------------------------------------------+
|                            HARDWARE ABSTRACTION LAYER (Vendor / HAL)                              |
|  [ AudioControl HAL ]                    [ Core Audio HAL ]                 [ Vehicle HAL (VHAL)] |
+---------------------------------------------------------------------------------------------------+

**Deploy as a Privileged System App via ADB:**
adb root && adb remount
adb shell mkdir -p /system/priv-app/CarAudioControl
adb push app/build/outputs/apk/debug/app-debug.apk /system/priv-app/CarAudioControl/CarAudioControl.apk
adb push etc/permissions/privapp-permissions-com.oem.audiocontrol.xml /system/etc/permissions/
adb push etc/car-framework/android.car.xml /system/etc/permissions/
adb reboot


Testing & Verification Commands
Inspect Active Audio Zones & Volume Group Mappings:

Bash
adb shell dumpsys car_service --services CarAudioService
Simulate Vehicle Suspend-to-RAM & Wake (CarPowerManager):

Bash
adb shell cmd car_service suspend
adb shell cmd car_service resume
Verify Privileged Permission Grants:

Bash
adb shell dumpsys package com.oem.audiocontrol | grep -A 10 "granted=true"








**DIAGRAMS**

1. High-Level System Architecture & API Connection Diagram
Plaintext
+---------------------------------------------------------------------------------------------------+
|                        PRIVILEGED SYSTEM APP LAYER (/system/priv-app)                             |
|                                   com.oem.audiocontrol                                            |
|                                                                                                   |
|  +---------------------------------------------------------------------------------------------+  |
|  |                        JETPACK COMPOSE UI & SCREEN FLOW LAYER                               |  |
|  |                                                                                             |  |
|  |  [ Screen 1: Primary Mixer ] <---> [ Screen 2: Rear Seat Zone ]                             |  |
|  |       |           ^                         |                                               |  |
|  |       v           |                         v                                               |  |
|  |  [ Screen 3: Focus & Chime ]       [ Screen 4: Power Suspend Overlay ]                      |  |
|  +---------------------------------------------------------------------------------------------+  |
|                                       |                    ^                                      |
|                                       | User Intents       | StateFlow<UiState>                   |
|                                       v                    |                                      |
|  +---------------------------------------------------------------------------------------------+  |
|  |                           VIEWMODEL & CONTROLLER LAYER                                      |  |
|  |                                                                                             |  |
|  |  AudioControlViewModel                                                                      |  |
|  |    ├──► CarAudioController  (Manages Zones, Volume Groups, Mute, DSP Snapshot)              |  |
|  |    └──► AudioFocusSimulator (Dispatches Nav Ducking & ADAS Safety Chime Requests)           |  |
|  +---------------------------------------------------------------------------------------------+  |
|             |                                  |                                  |               |
|             | CAR_CONTROL_AUDIO_VOLUME         | MODIFY_AUDIO_SETTINGS            | CAR_POWER     |
|             | CAR_CONTROL_AUDIO_SETTINGS       |                                  |               |
+-------------|----------------------------------|----------------------------------|---------------+
              | Binder IPC (android.car.jar)     | Binder IPC                       | Binder IPC
              v                                  v                                  v
+---------------------------------------------------------------------------------------------------+
|                           ANDROID AUTOMOTIVE FRAMEWORK (CarService)                               |
|                                                                                                   |
|  +-----------------------------------+   +-----------------------+   +-------------------------+  |
|  | CarAudioManager / CarAudioService |   | AudioManager / Policy |   | CarPowerManager / CPMS  |  |
|  | • getAudioZoneIds()               |   | • requestAudioFocus() |   | • setListener()         |  |
|  | • getVolumeGroupCount()           |   | • CarAudioFocus Matrix|   | • STATE_ON              |  |
|  | • setGroupVolume() / Mute()       |   | • Dynamic Mix Routing |   | • SHUTDOWN_PREPARE      |  |
|  | • CarVolumeCallback               |   | • ToneGenerator       |   | • SUSPEND_ENTER         |  |
|  +-----------------------------------+   +-----------------------+   +-------------------------+  |
+-------------------|----------------------------------|-----------------------------|--------------+
                    | HIDL / AIDL                      | AudioFlinger                | VHAL IPC
                    v                                  v                             v
+---------------------------------------------------------------------------------------------------+
|                            HARDWARE ABSTRACTION LAYER (Vendor / HAL)                              |
|                                                                                                   |
|  +-----------------------------------+   +-----------------------+   +-------------------------+  |
|  | AudioControl HAL                  |   | Core Audio HAL        |   | Vehicle HAL (VHAL)      |  |
|  | • Hardware Ducking Signals (-12dB)|   | • bus0_media_out      |   | • AP_POWER_STATE_REQ    |  |
|  | • Cabin Balance / Fade DSP Gain   |   | • bus1_nav_out        |   | • AP_POWER_STATE_REPORT |  |
|  | • Zone Amplifier Routing          |   | • bus2_safety_out     |   | • VMCU Ignition Signals |  |
|  +-----------------------------------+   +-----------------------+   +-------------------------+  |
+---------------------------------------------------------------------------------------------------+
2. End-to-End Screen Navigation & Event Flow
Plaintext
                        [ App Launch / Car.createCar() ]
                                       │
                        CarService Ready (STATE_ON)
                                       │
                                       ▼
        ┌─────────────────────────────────────────────────────────────┐
        │           SCREEN 1: Primary Cabin Mixer (Zone 0)            │
        │  • Displays Media, Nav, Voice, Safety Volume Group Sliders  │
        │  • Interactive 2D Cabin Sound Stage (Balance / Fade Canvas) │
        └───┬──────────────────────────┬──────────────────────────┬───┘
            │                          │                          │
   Tap [Rear Seat Zone 1]     Tap [Test Nav Ducking]     VMCU Ignition Off
   (Zone Selector Pill)       or [ADAS Chime]            (cmd car_service suspend)
            │                          │                          │
            ▼                          ▼                          ▼
┌───────────────────────┐  ┌───────────────────────┐  ┌───────────────────────┐
│ SCREEN 2: Rear Zone 1 │  │ SCREEN 3: Focus Matrix│  │ SCREEN 4: Power Sleep │
│ • Independent RSE     │  │ • Amber Priority      │  │ • Translucent Modal   │
│   Media/Game Sliders  │  │   Alert Banner        │  │   Over Active Cabin   │
│ • Driver Lock / Sync  │  │ • Media Ducked (-12dB)│  │ • Serializes Zone 0/1 │
│   & Mute Overrides    │  │ • Live Interaction Log│  │   & DSP to Storage    │
└───────────┬───────────┘  └───────────┬───────────┘  └───────────┬───────────┘
            │                          │                          │
   Tap [Primary Zone 0]       Auto-Release (2.5-3.0s)    VMCU Wake (STATE_ON)
            │                 abandonAudioFocusRequest()  restoreVolumesAfterWake()
            └──────────────────────────┴──────────────────────────┘
                                       │
                                       ▼
                        Returns to Active Cabin State