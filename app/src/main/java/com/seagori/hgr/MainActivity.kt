package com.seagori.hgr

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seagori.hgr.i18n.AppLocale
import com.seagori.hgr.i18n.LocalStrings
import com.seagori.hgr.shortcut.GoalShortcutHelper
import com.seagori.hgr.ui.AppViewModelFactory
import com.seagori.hgr.ui.navigation.AppNav
import com.seagori.hgr.ui.theme.Bg
import com.seagori.hgr.ui.theme.StartCoachTheme

class MainActivity : ComponentActivity() {
    private val triggerGoalId = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        enableEdgeToEdge()
        readTrigger(intent)
        val repository = (application as StartCoachApplication).container.repository
        val syncManager = (application as StartCoachApplication).container.syncManager
        val factory = AppViewModelFactory(repository, syncManager)
        setContent {
            val language by AppLocale.language.collectAsStateWithLifecycle()
            val strings = remember(language) { AppLocale.stringsFor(language) }
            val pendingGoalId by triggerGoalId
            CompositionLocalProvider(LocalStrings provides strings) {
                StartCoachTheme {
                    Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
                        AppNav(
                            factory = factory,
                            bootstrap = repository::bootstrap,
                            triggerGoalId = pendingGoalId,
                            onTriggerHandled = { triggerGoalId.value = null },
                            onOpenGoalFromTrigger = repository::openGoalFromTrigger
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readTrigger(intent)
    }

    private fun readTrigger(intent: Intent?) {
        val goalId = intent?.getLongExtra(GoalShortcutHelper.EXTRA_GOAL_ID, -1L) ?: -1L
        if (goalId > 0L) {
            triggerGoalId.value = goalId
        }
    }
}
