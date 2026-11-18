package com.oem.audiocontrol

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.oem.audiocontrol.ui.AudioControlViewModel
import com.oem.audiocontrol.ui.CabinAudioScreen

class AudioControlApp : Application()

class MainActivity : ComponentActivity() {
    private val viewModel: AudioControlViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CabinAudioScreen(viewModel = viewModel)
        }
    }
}