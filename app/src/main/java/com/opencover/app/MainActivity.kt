package com.opencover.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.opencover.app.ui.GameViewModel
import com.opencover.app.ui.GameViewModelFactory
import com.opencover.app.ui.OpenCoverAppRoot
import com.opencover.app.ui.Screen
import com.opencover.app.ui.multiplayer.MultiplayerViewModel
import com.opencover.app.ui.multiplayer.MultiplayerViewModelFactory
import com.opencover.app.ui.theme.OpenCoverTheme

class MainActivity : ComponentActivity() {

    private val viewModel: GameViewModel by viewModels {
        GameViewModelFactory((application as OpenCoverApp).container)
    }

    private val multiplayerViewModel: MultiplayerViewModel by viewModels {
        MultiplayerViewModelFactory((application as OpenCoverApp).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleInvitationIntent()
        setContent {
            OpenCoverTheme {
                OpenCoverAppRoot(
                    viewModel = viewModel,
                    multiplayerViewModel = multiplayerViewModel
                )
            }
        }
    }

    /** Intercepte un lien d'invitation opencover://join?code=…&server=… */
    private fun handleInvitationIntent() {
        val data = intent?.data ?: return
        if (data.scheme != "opencover" || data.host != "join") return
        viewModel.navigate(Screen.Multiplayer)
        multiplayerViewModel.handleDeepLink(
            code = data.getQueryParameter("code"),
            server = data.getQueryParameter("server")
        )
    }
}
