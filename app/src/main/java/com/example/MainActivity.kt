package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.media3.common.util.UnstableApi
import com.example.ui.screens.MainAppScreen
import com.example.ui.theme.CutsZoomTheme
import com.example.viewmodel.EditorViewModel

@UnstableApi
class MainActivity : ComponentActivity() {
    private val viewModel: EditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CutsZoomTheme {
                MainAppScreen(viewModel = viewModel)
            }
        }
    }
}
