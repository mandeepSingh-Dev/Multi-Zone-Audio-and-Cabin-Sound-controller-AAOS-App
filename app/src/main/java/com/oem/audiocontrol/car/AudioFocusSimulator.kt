package com.oem.audiocontrol.car

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AudioFocusSimulator(context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val scope = CoroutineScope(Dispatchers.Main)

    fun triggerNavigationDuckingPrompt(onStateChanged: (String?) -> Unit) {
        val navAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(navAttributes)
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener { }
            .build()

        val result = audioManager.requestAudioFocus(focusRequest)
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            onStateChanged("NAV DUCKING ACTIVE: Primary Media Attenuated (-12 dB)")
            playTone(AudioManager.STREAM_NOTIFICATION, ToneGenerator.TONE_PROP_BEEP2, 350)

            scope.launch {
                delay(3000L)
                audioManager.abandonAudioFocusRequest(focusRequest)
                onStateChanged(null)
            }
        }
    }

    fun triggerEmergencyAdasChime(onStateChanged: (String?) -> Unit) {
        val safetyAttributes = AudioAttributes.Builder()
//            .setUsage(AudioAttributes.USAGE_SAFETY)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(safetyAttributes)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener { }
            .build()

        val result = audioManager.requestAudioFocus(focusRequest)
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            onStateChanged("EMERGENCY / ADAS CHIME: All Entertainment Buses Suspended")
            playTone(AudioManager.STREAM_ALARM, ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 700)

            scope.launch {
                delay(2500L)
                audioManager.abandonAudioFocusRequest(focusRequest)
                onStateChanged(null)
            }
        }
    }

    private fun playTone(streamType: Int, toneType: Int, durationMs: Int) {
        try {
            val toneGen = ToneGenerator(streamType, 85)
            toneGen.startTone(toneType, durationMs)
            scope.launch {
                delay(durationMs + 100L)
                toneGen.release()
            }
        } catch (_: Exception) {
            // Safe fallback if virtual audio HAL device has no PCM output sink attached
        }
    }
}