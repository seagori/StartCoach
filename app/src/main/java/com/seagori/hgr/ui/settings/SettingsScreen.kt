package com.seagori.hgr.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seagori.hgr.domain.LlmProfile
import com.seagori.hgr.i18n.AppLanguage
import com.seagori.hgr.i18n.LocalStrings
import com.seagori.hgr.sync.AndroidSyncUi
import com.seagori.hgr.sync.DiscoveredServer
import com.seagori.hgr.sync.SyncCrypto
import com.seagori.hgr.sync.SyncProtocol
import com.seagori.hgr.ui.components.AppFilterChip
import com.seagori.hgr.ui.components.LicenseDialog
import com.seagori.hgr.ui.components.PartialMaskVisualTransformation
import com.seagori.hgr.ui.components.PrimaryButton
import com.seagori.hgr.ui.components.ScreenHeader
import com.seagori.hgr.ui.components.SecondaryButton
import com.seagori.hgr.ui.components.card
import com.seagori.hgr.ui.theme.AccentDeep
import com.seagori.hgr.ui.theme.AppThemePreset
import com.seagori.hgr.ui.theme.Card
import com.seagori.hgr.ui.theme.Ink
import com.seagori.hgr.ui.theme.Line
import com.seagori.hgr.ui.theme.Muted
import com.seagori.hgr.ui.theme.Soft
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val strings = LocalStrings.current
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val sync by viewModel.syncUi.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showLicenses by remember { mutableStateOf(false) }
    var licenseSections by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(showLicenses) {
        if (showLicenses && licenseSections.isEmpty()) {
            licenseSections = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                loadLicenseSections(context)
            }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> uri?.let(viewModel::requestExport) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::requestImport)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp)
    ) {
        ScreenHeader(title = strings.settingsTitle)
        Spacer(Modifier.height(18.dp))
        Text(strings.languageSection, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppLanguage.entries.forEach { language ->
                AppFilterChip(
                    selected = ui.language == language,
                    onClick = { viewModel.onLanguageChange(language) },
                    label = when (language) {
                        AppLanguage.SYSTEM -> strings.languageSystem
                        AppLanguage.KOREAN -> strings.languageKorean
                        AppLanguage.JAPANESE -> strings.languageJapanese
                        AppLanguage.CHINESE_SIMPLIFIED -> strings.languageChineseSimplified
                        AppLanguage.CHINESE_TRADITIONAL -> strings.languageChineseTraditional
                        AppLanguage.ENGLISH -> strings.languageEnglish
                    }
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(strings.themeSection, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppThemePreset.entries.forEach { preset ->
                AppFilterChip(
                    selected = ui.theme == preset,
                    onClick = { viewModel.onThemeChange(context, preset) },
                    label = when (preset) {
                        AppThemePreset.SAGE -> strings.themeSage
                        AppThemePreset.BLUE -> strings.themeBlue
                        AppThemePreset.DARK -> strings.themeDark
                    }
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(strings.modelConnection, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(8.dp))
        Text(
            strings.remotePrivacy,
            color = Muted,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(16.dp))
        ProfileEditor(
            title = strings.fastModelSection,
            note = strings.fastModelNote,
            apiUrl = ui.llm.fast.apiUrl,
            modelId = ui.llm.fast.modelId,
            tokenInput = ui.llm.fast.bearerTokenInput,
            hasSavedToken = ui.llm.fast.hasSavedToken,
            maskedToken = ui.llm.fast.maskedToken,
            urlPlaceholder = "https://bedrock-mantle.us-east-1.api.aws/openai/v1",
            modelPlaceholder = "google.gemma-4-26b-a4b",
            testing = ui.testingProfile == LlmProfile.FAST,
            onUrlChange = { viewModel.onUrlChange(LlmProfile.FAST, it) },
            onModelChange = { viewModel.onModelChange(LlmProfile.FAST, it) },
            onTokenChange = { viewModel.onTokenChange(LlmProfile.FAST, it) },
            onClearToken = { viewModel.clearToken(LlmProfile.FAST) },
            onTest = { viewModel.testConnection(LlmProfile.FAST) }
        )
        Spacer(Modifier.height(18.dp))
        ProfileEditor(
            title = strings.coachModelSection,
            note = strings.coachModelNote,
            apiUrl = ui.llm.coach.apiUrl,
            modelId = ui.llm.coach.modelId,
            tokenInput = ui.llm.coach.bearerTokenInput,
            hasSavedToken = ui.llm.coach.hasSavedToken,
            maskedToken = ui.llm.coach.maskedToken,
            urlPlaceholder = "https://bedrock-mantle.us-east-1.api.aws/openai/v1",
            modelPlaceholder = "google.gemma-4-26b-a4b",
            testing = ui.testingProfile == LlmProfile.COACH,
            reasoning = ui.llm.coach.reasoningEffort,
            onReasoningChange = viewModel::onReasoningChange,
            onUrlChange = { viewModel.onUrlChange(LlmProfile.COACH, it) },
            onModelChange = { viewModel.onModelChange(LlmProfile.COACH, it) },
            onTokenChange = { viewModel.onTokenChange(LlmProfile.COACH, it) },
            onClearToken = { viewModel.clearToken(LlmProfile.COACH) },
            onTest = { viewModel.testConnection(LlmProfile.COACH) }
        )
        Spacer(Modifier.height(14.dp))
        PrimaryButton(if (ui.saved) strings.saved else strings.saveConnection, onClick = viewModel::save)
        ui.testMessage?.let {
            Text(it, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(28.dp))
        SyncSection(viewModel, sync)
        Spacer(Modifier.height(28.dp))
        Text(strings.dataManagement, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(8.dp))
        Text(strings.backupDescription, color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        SecondaryButton(strings.backupExport) {
            val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.KOREA).format(Date())
            exportLauncher.launch("onestep-backup-$stamp.zip")
        }
        Spacer(Modifier.height(10.dp))
        SecondaryButton(strings.backupImportReplace) {
            importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
        }
        ui.backupMessage?.let {
            Text(it, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(12.dp))
        SecondaryButton(strings.clearAllRecords, onClick = viewModel::clearData)
        Spacer(Modifier.height(28.dp))
        var pendingPermissionTarget by remember { mutableStateOf<String?>(null) }
        val notificationPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                when (pendingPermissionTarget) {
                    "regular" -> viewModel.onReminderEnabledChange(context, true)
                    "daily" -> viewModel.onDailyReminderEnabledChange(context, true)
                    "pinned" -> viewModel.onPinnedDailyEnabledChange(context, true)
                }
            }
            pendingPermissionTarget = null
        }
        Text(strings.reminderSection, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(strings.reminderToggle, color = Ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = ui.reminderEnabled,
                onCheckedChange = { enabled ->
                    if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!granted) {
                            pendingPermissionTarget = "regular"
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            return@Switch
                        }
                    }
                    viewModel.onReminderEnabledChange(context, enabled)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Card,
                    checkedTrackColor = AccentDeep
                )
            )
        }
        if (ui.reminderEnabled) {
            Spacer(Modifier.height(10.dp))
            IntervalSelector(
                label = strings.reminderRetryLabel,
                value = ui.reminderRetryMinutes,
                presets = listOf(10, 15, 30),
                unitLabel = strings::minutesShort,
                customLabel = strings.customOption,
                onSelect = { viewModel.onReminderRetryChange(context, it) }
            )
            Spacer(Modifier.height(10.dp))
            IntervalSelector(
                label = strings.reminderCooldown,
                value = ui.reminderCooldownMinutes,
                presets = listOf(60, 120, 180),
                unitLabel = strings::minutesShort,
                customLabel = strings.customOption,
                onSelect = { viewModel.onReminderCooldownChange(context, it) }
            )
            Spacer(Modifier.height(10.dp))
            IntervalSelector(
                label = strings.reminderDurationLabel,
                value = ui.reminderDurationSeconds,
                presets = listOf(5, 15, 30),
                unitLabel = strings::secondsShort,
                customLabel = strings.customOption,
                onSelect = { viewModel.onReminderDurationChange(context, it) }
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = ui.reminderMessage,
                onValueChange = { viewModel.onReminderMessageChange(context, it) },
                label = { Text(strings.reminderMessageLabel) },
                placeholder = { Text(strings.reminderDefaultText) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Text(strings.reminderAutoDismissNote, color = Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(24.dp))
        Text(strings.dailyReminderSection, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(strings.dailyReminderToggle, color = Ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = ui.dailyReminderEnabled,
                onCheckedChange = { enabled ->
                    if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!granted) {
                            pendingPermissionTarget = "daily"
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            return@Switch
                        }
                    }
                    viewModel.onDailyReminderEnabledChange(context, enabled)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Card,
                    checkedTrackColor = AccentDeep
                )
            )
        }
        if (ui.dailyReminderEnabled) {
            Spacer(Modifier.height(10.dp))
            IntervalSelector(
                label = strings.reminderRetryLabel,
                value = ui.dailyReminderRetryMinutes,
                presets = listOf(10, 15, 30),
                unitLabel = strings::minutesShort,
                customLabel = strings.customOption,
                onSelect = { viewModel.onDailyReminderRetryChange(context, it) }
            )
            Spacer(Modifier.height(10.dp))
            IntervalSelector(
                label = strings.reminderCooldown,
                value = ui.dailyReminderCooldownMinutes,
                presets = listOf(60, 120, 180),
                unitLabel = strings::minutesShort,
                customLabel = strings.customOption,
                onSelect = { viewModel.onDailyReminderCooldownChange(context, it) }
            )
            Spacer(Modifier.height(10.dp))
            IntervalSelector(
                label = strings.reminderDurationLabel,
                value = ui.dailyReminderDurationSeconds,
                presets = listOf(5, 15, 30),
                unitLabel = strings::secondsShort,
                customLabel = strings.customOption,
                onSelect = { viewModel.onDailyReminderDurationChange(context, it) }
            )
            Spacer(Modifier.height(6.dp))
            Text("${strings.dailyReminderMessage} · ${strings.reminderAutoDismissNote}", color = Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(28.dp))
        Text(strings.pinnedDailySection, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(strings.pinnedDailyToggle, color = Ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = ui.pinnedDailyEnabled,
                onCheckedChange = { enabled ->
                    if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!granted) {
                            pendingPermissionTarget = "pinned"
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            return@Switch
                        }
                    }
                    viewModel.onPinnedDailyEnabledChange(context, enabled)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Card,
                    checkedTrackColor = AccentDeep
                )
            )
        }
        Spacer(Modifier.height(28.dp))
        Text(strings.licenseSection, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(10.dp))
        SecondaryButton(strings.licenseInfo) { showLicenses = true }
    }

    if (showLicenses && licenseSections.isNotEmpty()) {
        LicenseDialog(
            title = strings.licenseInfo,
            sections = licenseSections,
            closeLabel = strings.close,
            onDismiss = { showLicenses = false }
        )
    }

    val backupDialogOpen = ui.pendingExportUri != null || ui.pendingImportUri != null
    if (backupDialogOpen) {
        val exporting = ui.pendingExportUri != null
        AlertDialog(
            onDismissRequest = viewModel::cancelBackupDialog,
            title = { Text(if (exporting) strings.backupPasswordTitle else strings.backupImportTitle) },
            containerColor = Card,
            titleContentColor = Ink,
            textContentColor = Ink,
            tonalElevation = 0.dp,
            text = {
                Column {
                    Text(
                        if (exporting) strings.backupExportExplanation else strings.backupImportExplanation,
                        color = Muted,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = ui.backupPassword,
                        onValueChange = viewModel::onBackupPasswordChange,
                        label = { Text(strings.backupPasswordTitle) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (exporting) viewModel.confirmBackupExport() else viewModel.confirmBackupImport()
                }) {
                    Text(if (exporting) strings.export else strings.import)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelBackupDialog) { Text(strings.cancel) }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SyncSection(viewModel: SettingsViewModel, sync: AndroidSyncUi) {
    val strings = LocalStrings.current
    var pairingServer by remember { mutableStateOf<DiscoveredServer?>(null) }
    var pairingCode by remember { mutableStateOf("") }
    var pairingFingerprint by remember { mutableStateOf("") }
    var manualAddress by remember { mutableStateOf("") }
    var manualFingerprint by remember { mutableStateOf("") }
    var showScanner by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.startSyncDiscovery()
    }

    Text(strings.syncSection, fontWeight = FontWeight.Bold, color = Ink)
    Spacer(Modifier.height(8.dp))
    Text(strings.syncDescription, color = Muted, fontSize = 13.sp)
    Spacer(Modifier.height(12.dp))

    if (sync.paired) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .card(20.dp)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    sync.serverName.ifBlank { strings.syncPairedTo },
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                    modifier = Modifier.weight(1f)
                )
                Text(strings.syncPairedTo, color = AccentDeep, fontSize = 12.sp)
            }
            Spacer(Modifier.height(6.dp))
            Text(strings.syncFingerprint, color = Muted, fontSize = 11.sp)
            Text(SyncCrypto.groupedFingerprint(sync.serverFingerprint), color = Ink, fontSize = 11.sp)
            Text(strings.syncFingerprintNote, color = Muted, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
            val format = remember {
                SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault())
            }
            val last = if (sync.lastSyncAt > 0) format.format(Date(sync.lastSyncAt)) else strings.syncNever
            Text("${strings.syncLastSync}: $last", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                text = if (sync.syncing) strings.syncSyncing else strings.syncNow,
                enabled = !sync.syncing
            ) { viewModel.syncNow() }
            Spacer(Modifier.height(8.dp))
            SecondaryButton(strings.syncForget) { viewModel.forgetSyncServer() }
        }
    } else {
        Text(strings.syncNotPaired, color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        SecondaryButton(strings.syncQrScan) { showScanner = true }
        Spacer(Modifier.height(8.dp))
        SecondaryButton(
            text = if (sync.discovering) strings.syncStopDiscover else strings.syncDiscover
        ) {
            if (sync.discovering) {
                viewModel.stopSyncDiscovery()
            } else if (viewModel.needsLocalNetworkPermission()) {
                permissionLauncher.launch(LOCAL_NETWORK_PERMISSION)
            } else {
                viewModel.startSyncDiscovery()
            }
        }
        if (viewModel.needsLocalNetworkPermission()) {
            Spacer(Modifier.height(6.dp))
            Text(strings.syncPermissionRequired, color = Muted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(10.dp))
        if (sync.discovering) {
            Text(strings.syncDiscovering, color = Muted, fontSize = 12.sp)
        }
        if (sync.servers.isNotEmpty()) {
            Text(strings.syncDiscovered, color = Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            sync.servers.forEach { server ->
                val selectable = SyncCrypto.normalizeFingerprint(server.fingerprint).isNotBlank()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .card()
                        .then(
                            if (selectable) {
                                Modifier.clickable {
                                    pairingServer = server
                                    pairingCode = ""
                                    pairingFingerprint = ""
                                }
                            } else {
                                Modifier
                            }
                        )
                        .padding(14.dp)
                ) {
                    Text(server.name, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("${server.host}:${server.port}", color = Muted, fontSize = 11.sp)
                    if (server.fingerprint.isNotBlank()) {
                        Text(
                            SyncCrypto.groupedFingerprint(server.fingerprint),
                            color = Muted,
                            fontSize = 10.sp
                        )
                    } else {
                        Text(strings.syncNoFingerprint, color = Muted, fontSize = 10.sp)
                    }
                }
            }
        } else if (!sync.discovering) {
            Text(strings.syncNoServersFound, color = Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = manualAddress,
            onValueChange = { manualAddress = it },
            label = { Text(strings.syncManualAddress) },
            placeholder = { Text(strings.syncManualHint) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = manualFingerprint,
            onValueChange = { manualFingerprint = it },
            label = { Text(strings.syncFingerprint) },
            placeholder = { Text(strings.syncFingerprintNote) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        SecondaryButton(strings.syncPair) {
            val trimmed = manualAddress.trim()
            val host = trimmed.substringBeforeLast(':', trimmed).trim()
            val port = trimmed.substringAfterLast(':', "").toIntOrNull() ?: SyncProtocol.DEFAULT_PORT
            val fp = SyncCrypto.normalizeFingerprint(manualFingerprint)
            if (host.isBlank() || fp.isBlank()) {
                pairingServer = null
            } else {
                pairingServer = DiscoveredServer(
                    deviceId = "",
                    name = host,
                    host = host,
                    port = port,
                    fingerprint = fp
                )
                pairingCode = ""
                pairingFingerprint = fp
            }
        }
    }

    if (sync.pairing) {
        Spacer(Modifier.height(8.dp))
        Text(strings.syncPairing, color = Muted, fontSize = 12.sp)
    }
    sync.message?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, color = Muted, fontSize = 12.sp)
    }

    pairingServer?.let { server ->
        AlertDialog(
            onDismissRequest = { pairingServer = null },
            title = { Text(strings.syncPair) },
            containerColor = Card,
            titleContentColor = Ink,
            textContentColor = Ink,
            tonalElevation = 0.dp,
            text = {
                Column {
                    Text(server.name, fontWeight = FontWeight.Bold, color = Ink)
                    Text("${server.host}:${server.port}", color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(strings.syncPairCodeHint, color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pairingCode,
                        onValueChange = { if (it.length <= 12) pairingCode = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (SyncCrypto.normalizeFingerprint(server.fingerprint).isBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(strings.syncFingerprint, color = Muted, fontSize = 12.sp)
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = pairingFingerprint,
                            onValueChange = { pairingFingerprint = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                val pin = SyncCrypto.normalizeFingerprint(
                    server.fingerprint.ifBlank { pairingFingerprint }
                )
                TextButton(
                    enabled = pairingCode.isNotBlank() && pin.isNotBlank() && !sync.pairing,
                    onClick = {
                        if (server.deviceId.isBlank()) {
                            viewModel.pairSyncManual("${server.host}:${server.port}", pairingCode, pin)
                        } else {
                            viewModel.pairSync(server.copy(fingerprint = pin), pairingCode)
                        }
                        pairingServer = null
                    }
                ) { Text(strings.syncPair) }
            },
            dismissButton = {
                TextButton(onClick = { pairingServer = null }) { Text(strings.cancel) }
            }
        )
    }

    if (showScanner) {
        QrScannerDialog(
            onDismiss = { showScanner = false },
            onScanned = { text ->
                showScanner = false
                viewModel.pairSyncFromQr(text)
            }
        )
    }
}

private const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileEditor(
    title: String,
    note: String,
    apiUrl: String,
    modelId: String,
    tokenInput: String,
    hasSavedToken: Boolean,
    maskedToken: String,
    urlPlaceholder: String,
    modelPlaceholder: String,
    testing: Boolean,
    onUrlChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onClearToken: () -> Unit,
    onTest: () -> Unit,
    reasoning: String? = null,
    onReasoningChange: (String) -> Unit = {}
) {
    val strings = LocalStrings.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .card(20.dp)
            .padding(16.dp)
    ) {
        Text(title, fontWeight = FontWeight.Bold, color = Ink, fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Text(note, color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = apiUrl,
            onValueChange = onUrlChange,
            label = { Text(strings.apiUrlLabel) },
            placeholder = { Text(urlPlaceholder) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = modelId,
            onValueChange = onModelChange,
            label = { Text(strings.modelIdLabel) },
            placeholder = { Text(modelPlaceholder) },
            modifier = Modifier.fillMaxWidth()
        )
        TextButton(onClick = {
            onUrlChange(urlPlaceholder)
            onModelChange(modelPlaceholder)
        }) {
            Text(strings.useDefaultProfile, color = AccentDeep, fontSize = 12.sp)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = tokenInput,
            onValueChange = onTokenChange,
            label = { Text(strings.apiKeyLabel) },
            placeholder = {
                Text(if (hasSavedToken) strings.apiKeySavedHint else strings.apiKeyInputHint)
            },
            visualTransformation = PartialMaskVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (hasSavedToken && maskedToken.isNotBlank() && tokenInput.isBlank()) {
            Spacer(Modifier.height(5.dp))
            Text(
                "${strings.savedKeyLabel}: $maskedToken",
                color = Muted,
                fontSize = 12.sp
            )
        }
        if (hasSavedToken) {
            Spacer(Modifier.height(8.dp))
            SecondaryButton(strings.removeSavedKey, onClick = onClearToken)
        }
        if (reasoning != null) {
            Spacer(Modifier.height(12.dp))
            Text(strings.coachReasoning, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("low", "medium", "high").forEach { value ->
                    AppFilterChip(
                        selected = reasoning == value,
                        onClick = { onReasoningChange(value) },
                        label = when (value) {
                            "low" -> strings.reasoningLow
                            "high" -> strings.reasoningHigh
                            else -> strings.reasoningMedium
                        }
                    )
                }
            }
            Spacer(Modifier.height(5.dp))
            Text(strings.coachReasoningNote, color = Muted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(12.dp))
        SecondaryButton(
            text = if (testing) strings.testingConnection else strings.testConnection,
            loading = testing,
            enabled = !testing,
            onClick = onTest
        )
    }
}

private val LICENSE_FILES = listOf(
    "NOTICE" to "licenses/NOTICE.txt",
    "GPL-3.0" to "licenses/GPL-3.0.txt",
    "CC-BY-SA-4.0" to "licenses/CC-BY-SA-4.0.txt",
    "Third-Party" to "licenses/THIRD_PARTY_NOTICES.txt",
    "Apache-2.0" to "licenses/Apache-2.0.txt",
    "Bouncy Castle" to "licenses/Bouncy-Castle.txt"
)

private fun loadLicenseSections(context: Context): List<Pair<String, String>> =
    LICENSE_FILES.map { (label, path) ->
        val body = runCatching {
            context.assets.open(path).bufferedReader().use { it.readText() }
        }.getOrElse { path }
        label to body
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IntervalSelector(
    label: String,
    value: Int,
    presets: List<Int>,
    unitLabel: (Int) -> String,
    customLabel: String,
    onSelect: (Int) -> Unit
) {
    val strings = LocalStrings.current
    var showCustomDialog by remember { mutableStateOf(false) }
    var customInput by remember(value) { mutableStateOf(value.toString()) }
    val isCustom = value !in presets

    Text(label, color = Muted, fontSize = 13.sp)
    Spacer(Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        presets.forEach { preset ->
            AppFilterChip(
                selected = value == preset,
                onClick = { onSelect(preset) },
                label = unitLabel(preset)
            )
        }
        AppFilterChip(
            selected = isCustom,
            onClick = {
                customInput = value.toString()
                showCustomDialog = true
            },
            label = if (isCustom) "$customLabel (${unitLabel(value)})" else customLabel
        )
    }

    if (showCustomDialog) {
        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            containerColor = Card,
            titleContentColor = Ink,
            textContentColor = Ink,
            tonalElevation = 0.dp,
            title = { Text(label) },
            text = {
                val currentNum = (customInput.toIntOrNull() ?: value).coerceAtLeast(1)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = customInput,
                        onValueChange = { next -> customInput = next.filter { it.isDigit() }.take(4) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { customInput = (currentNum - 5).coerceAtLeast(1).toString() }) {
                            Text("-5", color = AccentDeep, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { customInput = (currentNum - 1).coerceAtLeast(1).toString() }) {
                            Text("-1", color = AccentDeep, fontWeight = FontWeight.Bold)
                        }
                        Text(unitLabel(currentNum), color = Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = { customInput = (currentNum + 1).coerceAtMost(9999).toString() }) {
                            Text("+1", color = AccentDeep, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { customInput = (currentNum + 5).coerceAtMost(9999).toString() }) {
                            Text("+5", color = AccentDeep, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsed = customInput.toIntOrNull()
                    if (parsed != null && parsed >= 1) {
                        onSelect(parsed)
                    }
                    showCustomDialog = false
                }) {
                    Text(strings.save)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDialog = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }
}
