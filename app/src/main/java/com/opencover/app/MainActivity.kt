package com.opencover.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.opencover.app.ui.GameViewModel
import com.opencover.app.ui.GameViewModelFactory
import com.opencover.app.ui.OpenCoverAppRoot
import com.opencover.app.ui.theme.OpenCoverTheme

class MainActivity : ComponentActivity() {

    private val viewModel: GameViewModel by viewModels {
        GameViewModelFactory((application as OpenCoverApp).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OpenCoverTheme {
                OpenCoverAppRoot(viewModel = viewModel)
            }
        }
    }
}
