package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.ui.SamsungMusicApp
import com.example.ui.SamsungMusicViewModel
import com.example.ui.theme.SamsungMusicTheme

class MainActivity : ComponentActivity() {

    private val viewModel: SamsungMusicViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SamsungMusicTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SamsungMusicApp(viewModel = viewModel)
                }
            }
        }
    }
}
