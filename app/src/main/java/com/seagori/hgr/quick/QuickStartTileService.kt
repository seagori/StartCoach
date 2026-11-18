package com.seagori.hgr.quick

import android.app.PendingIntent
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.seagori.hgr.R
import com.seagori.hgr.StartCoachApplication
import com.seagori.hgr.i18n.AppLocale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 상단 빠른 설정(Quick Settings) 타일.
 * 탭하면 화면 전환 없이 반투명 플로팅 바텀시트(QuickOverlayActivity)를 띄운다.
 */
class QuickStartTileService : TileService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        tile.state = Tile.STATE_ACTIVE
        tile.icon = Icon.createWithResource(this, R.drawable.ic_quick_tile)
        tile.label = getString(R.string.quick_tile_label)
        tile.updateTile()

        serviceScope.launch {
            val container = (application as StartCoachApplication).container
            val goal = runCatching { container.db.goalDao().observeActive().first().firstOrNull() }.getOrNull()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = goal?.title ?: AppLocale.strings.quickStartButton
            }
            tile.updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) {
            unlockAndRun { launchOverlay() }
        } else {
            launchOverlay()
        }
    }

    private fun launchOverlay() {
        val intent = Intent(this, QuickOverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        if (Build.VERSION.SDK_INT >= 34) {
            val pending = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        fun isTileEnabled(context: Context): Boolean {
            val component = ComponentName(context, QuickStartTileService::class.java)
            val state = context.packageManager.getComponentEnabledSetting(component)
            return state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }

        fun setTileEnabled(context: Context, enabled: Boolean) {
            val component = ComponentName(context, QuickStartTileService::class.java)
            val newState = if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            context.packageManager.setComponentEnabledSetting(
                component,
                newState,
                PackageManager.DONT_KILL_APP
            )
        }

        fun canRequestAddTile(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

        fun requestAddTile(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
            val manager = context.getSystemService(StatusBarManager::class.java) ?: return
            val component = ComponentName(context, QuickStartTileService::class.java)
            manager.requestAddTileService(
                component,
                context.getString(R.string.quick_tile_label),
                Icon.createWithResource(context, R.drawable.ic_quick_tile),
                context.mainExecutor
            ) {}
        }
    }
}
