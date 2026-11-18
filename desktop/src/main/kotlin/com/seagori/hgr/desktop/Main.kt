package com.seagori.hgr.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.seagori.hgr.data.settings.SettingsStore
import com.seagori.hgr.i18n.AppLocale
import com.seagori.hgr.i18n.LocalStrings
import com.seagori.hgr.ui.theme.AccentDeep
import com.seagori.hgr.ui.theme.Bg
import com.seagori.hgr.ui.theme.Card
import com.seagori.hgr.ui.theme.Ink
import com.seagori.hgr.ui.theme.Line
import com.seagori.hgr.ui.theme.Muted
import com.seagori.hgr.ui.theme.Soft
import com.seagori.hgr.ui.theme.StartCoachTheme
import kotlinx.coroutines.delay
import org.jetbrains.skia.Image
import java.awt.Dimension
import java.awt.GraphicsEnvironment

private fun loadAppIcon(): BitmapPainter? = runCatching {
    Thread.currentThread().contextClassLoader
        ?.getResourceAsStream("startcoach-icon.png")
        ?.use { BitmapPainter(Image.makeFromEncoded(it.readBytes()).toComposeImageBitmap()) }
}.getOrNull()

@OptIn(ExperimentalComposeUiApi::class)
fun main() = application {
    System.setProperty("java.net.useSystemProxies", "true")
    val settings = remember { SettingsStore() }
    remember { AppLocale.init(settings.appLanguage) }
    val language by AppLocale.language.collectAsState()
    val strings = remember(language) { AppLocale.stringsFor(language) }
    val state = remember { DesktopAppState(settings) }
    val windowState = rememberWindowState(size = DpSize(390.dp, 644.dp))
    val appIcon = remember { loadAppIcon() }

    var windowVisible by remember { mutableStateOf(true) }
    val traySupported = remember(appIcon) { appIcon != null && java.awt.SystemTray.isSupported() }

    if (traySupported && appIcon != null) {
        Tray(
            icon = appIcon,
            tooltip = strings.appName,
            onAction = {
                windowState.isMinimized = false
                windowVisible = true
            }
        ) {
            Item(strings.desktopTrayOpen) {
                windowState.isMinimized = false
                windowVisible = true
            }
            CheckboxItem(
                text = strings.desktopMiniBarMode,
                checked = state.miniBarEnabled,
                onCheckedChange = { state.miniBarEnabled = it }
            )
            Separator()
            Item(strings.desktopTrayExit) {
                state.dispose()
                exitApplication()
            }
        }
    }

    Window(
        onCloseRequest = {
            if (traySupported) {
                windowVisible = false
            } else {
                state.dispose()
                exitApplication()
            }
        },
        visible = windowVisible,
        title = strings.appName,
        icon = appIcon,
        state = windowState
    ) {
        LaunchedEffect(windowVisible) {
            if (!windowVisible) return@LaunchedEffect
            window.minimumSize = Dimension(360, 640)
            window.isAlwaysOnTop = true
            val bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
            window.setLocation(
                bounds.x + ((bounds.width - window.width) / 2).coerceAtLeast(0),
                bounds.y + 16
            )
            repeat(3) {
                window.toFront()
                delay(180)
            }
        }
        CompositionLocalProvider(LocalStrings provides strings) {
            StartCoachTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
                    DesktopApp(state)
                }
            }
        }
    }

    if (state.miniBarEnabled) {
        var hovered by remember { mutableStateOf(false) }
        val barWidth = 420.dp
        val barHeight = if (hovered) 142.dp else 44.dp
        val bounds = remember { GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds }
        val miniState = rememberWindowState(
            size = DpSize(barWidth, barHeight),
            position = WindowPosition(
                x = (bounds.x + ((bounds.width - 420) / 2).coerceAtLeast(0)).dp,
                y = (bounds.y + 4).dp
            )
        )
        LaunchedEffect(hovered) {
            miniState.size = DpSize(barWidth, if (hovered) 142.dp else 44.dp)
        }
        Window(
            onCloseRequest = { state.miniBarEnabled = false },
            state = miniState,
            undecorated = true,
            transparent = true,
            alwaysOnTop = true,
            resizable = false,
            focusable = false
        ) {
            val latest = state.activeGoals.firstOrNull()
            val cached = latest?.let { state.easiestCachedAction(it.id) }
            val stepTitle = latest?.let { state.pathForGoal(it.id)?.current?.title }
            val actionText = cached ?: stepTitle ?: latest?.title ?: strings.quickStartFallback

            CompositionLocalProvider(LocalStrings provides strings) {
                StartCoachTheme {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Card.copy(alpha = 0.96f))
                            .border(1.dp, Line, RoundedCornerShape(16.dp))
                            .onPointerEvent(PointerEventType.Enter) { hovered = true }
                            .onPointerEvent(PointerEventType.Exit) { hovered = false }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${strings.desktopMiniNext}$actionText",
                                color = Ink,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = strings.desktopMiniStart,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(AccentDeep)
                                    .clickable {
                                        if (latest != null && (cached != null || stepTitle != null)) {
                                            state.startQuickAction(latest.id, actionText)
                                        } else if (latest != null) {
                                            state.startGoal(latest.title)
                                        }
                                        windowState.isMinimized = false
                                        windowVisible = true
                                    }
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                        if (hovered) {
                            Spacer(Modifier.height(10.dp))
                            latest?.let {
                                Text(
                                    text = it.title,
                                    color = Muted,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Text(
                                    text = strings.desktopTrayOpen,
                                    color = AccentDeep,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Soft)
                                        .clickable {
                                            windowState.isMinimized = false
                                            windowVisible = true
                                        }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
