package com.lemytho.app

import android.app.Application
import com.lemytho.app.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LeMythoApp : Application() {

    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Seed/re-seed de la base de mots si la version embarquée est plus récente.
        applicationScope.launch {
            container.wordRepository.seedIfNeeded()
        }
    }
}
