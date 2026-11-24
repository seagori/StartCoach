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
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.LinearProgressIndicator
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
import com.seagori.hgr.ui.theme.AppTheme
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
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

private fun loadAppIcon(): BitmapPainter? = runCatching {
    Thread.currentThread().contextClassLoader
        ?.getResourceAsStream("startcoach-icon.png")
        ?.use { BitmapPainter(Image.makeFromEncoded(it.readBytes()).toComposeImageBitmap()) }
}.getOrNull()

private fun acquireSingleInstanceLock(): FileChannel? = runCatching {
    val dir = File(System.getProperty("user.home"), ".startcoach").apply { mkdirs() }
    val channel = FileChannel.open(
        File(dir, "desktop.lock").toPath(),
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE
    )
    if (channel.tryLock() == null) {
        channel.close()
        null
    } else {
        channel
    }
}.getOrNull()

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val instanceLock = acquireSingleInstanceLock() ?: return
    Runtime.getRuntime().addShutdownHook(Thread { runCatching { instanceLock.close() } })
    application {
    System.setProperty("java.net.useSystemProxies", "true")
    val settings = remember { SettingsStore() }
    remember {
        AppLocale.init(settings.appLanguage)
        AppTheme.init(settings.appTheme)
    }
    val language by AppLocale.language.collectAsState()
    val themePreset by AppTheme.preset.collectAsState()
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
            androidx.compose.runtime.key(themePreset) {
                StartCoachTheme {
                    Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
                        DesktopApp(state)
                    }
                }
            }
        }
    }

    if (state.miniBarEnabled) {
        val barWidth = 340.dp
        val barHeight = 52.dp
        val bounds = remember { GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds }
        val miniState = rememberWindowState(
            size = DpSize(barWidth, barHeight),
            position = WindowPosition(
                x = (bounds.x + ((bounds.width - 340) / 2).coerceAtLeast(0)).dp,
                y = (bounds.y + 8).dp
            )
        )
        Window(
            onCloseRequest = { state.miniBarEnabled = false },
            state = miniState,
            undecorated = true,
            transparent = true,
            alwaysOnTop = true,
            resizable = false,
            focusable = false
        ) {
            val daily = state.data.dailyTasks
            val completedIds = daily.effectiveCompletedIds()
            val percent = daily.todayPercent()

            CompositionLocalProvider(LocalStrings provides strings) {
                androidx.compose.runtime.key(themePreset) {
                    StartCoachTheme {
                        WindowDraggableArea {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Card.copy(alpha = 0.96f))
                                    .border(1.dp, Line, RoundedCornerShape(14.dp))
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = strings.dailyProgressLabel(completedIds.size, daily.tasks.size, percent),
                                        color = Ink,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "$percent%",
                                        color = AccentDeep,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = strings.navDaily,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(AccentDeep)
                                            .clickable {
                                                state.screen = DesktopScreen.DAILY
                                                windowState.isMinimized = false
                                                windowVisible = true
                                            }
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { (percent / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(99.dp)),
                                    color = AccentDeep,
                                    trackColor = Soft
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val reminderText = state.activeReminderText
    if (reminderText != null) {
        val maxBounds = remember {
            GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        }
        val bannerWidth = 360.dp
        val bannerState = rememberWindowState(
            size = DpSize(bannerWidth, 68.dp),
            position = WindowPosition(
                x = (maxBounds.x + maxBounds.width - 360 - 16).coerceAtLeast(0).dp,
                y = (maxBounds.y + maxBounds.height - 68 - 16).coerceAtLeast(0).dp
            )
        )
        Window(
            onCloseRequest = { state.dismissReminder() },
            state = bannerState,
            undecorated = true,
            transparent = true,
            alwaysOnTop = true,
            resizable = false,
            focusable = false
        ) {
            CompositionLocalProvider(LocalStrings provides strings) {
                StartCoachTheme {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Card.copy(alpha = 0.98f))
                            .border(1.dp, AccentDeep, RoundedCornerShape(16.dp))
                            .clickable {
                                state.dismissReminder()
                                windowState.isMinimized = false
                                windowVisible = true
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.appName,
                                color = AccentDeep,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = reminderText,
                                color = Ink,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
}
