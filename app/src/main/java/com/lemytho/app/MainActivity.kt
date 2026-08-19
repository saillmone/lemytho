package com.lemytho.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.lemytho.app.ui.GameViewModel
import com.lemytho.app.ui.GameViewModelFactory
import com.lemytho.app.ui.LeMythoAppRoot
import com.lemytho.app.ui.Screen
import com.lemytho.app.ui.multiplayer.MultiplayerViewModel
import com.lemytho.app.ui.multiplayer.MultiplayerViewModelFactory
import com.lemytho.app.ui.theme.LeMythoTheme

class MainActivity : ComponentActivity() {

    private val viewModel: GameViewModel by viewModels {
        GameViewModelFactory((application as LeMythoApp).container)
    }

    private val multiplayerViewModel: MultiplayerViewModel by viewModels {
        MultiplayerViewModelFactory((application as LeMythoApp).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleInvitationIntent()
        setContent {
            LeMythoTheme {
                LeMythoAppRoot(
                    viewModel = viewModel,
                    multiplayerViewModel = multiplayerViewModel
                )
            }
        }
    }

    /** Intercepte un lien d'invitation lemytho://join?code=…&server=… */
    private fun handleInvitationIntent() {
        val data = intent?.data ?: return
        if (data.scheme != "lemytho" || data.host != "join") return
        viewModel.navigate(Screen.Multiplayer)
        multiplayerViewModel.handleDeepLink(
            code = data.getQueryParameter("code"),
            server = data.getQueryParameter("server")
        )
    }
}
