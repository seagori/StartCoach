package com.seagori.hgr.sync

import android.content.Context
import android.content.pm.PackageManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.seagori.hgr.data.local.AppDatabase
import com.seagori.hgr.i18n.AppLocale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

data class DiscoveredServer(
    val deviceId: String,
    val name: String,
    val host: String,
    val port: Int,
    val fingerprint: String
)

data class AndroidSyncUi(
    val discovering: Boolean = false,
    val servers: List<DiscoveredServer> = emptyList(),
    val paired: Boolean = false,
    val serverName: String = "",
    val serverFingerprint: String = "",
    val lastSyncAt: Long = 0,
    val pairing: Boolean = false,
    val syncing: Boolean = false,
    val message: String? = null
)

class AndroidSyncManager(
    private val context: Context,
    db: AppDatabase,
    settings: com.seagori.hgr.data.settings.SettingsStore,
    tokens: com.seagori.hgr.data.settings.SecureTokenStore
) {
    private val store = AndroidSyncStore(context)
    private val adapter = AndroidSyncAdapter(db, store.deviceId, settings, tokens)
    private val controller = SyncController(adapter)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val syncMutex = Mutex()
    private val servers = LinkedHashMap<String, DiscoveredServer>()
    private val resolving = mutableSetOf<String>()
    private val serviceNames = mutableMapOf<String, String>()
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    private val _ui = MutableStateFlow(
        store.clientState.let {
            AndroidSyncUi(
                paired = it.paired,
                serverName = it.serverName,
                serverFingerprint = it.serverFingerprint,
                lastSyncAt = it.lastSyncAt
            )
        }
    )
    val ui: StateFlow<AndroidSyncUi> = _ui

    fun isPaired(): Boolean = store.clientState.paired

    fun needsLocalNetworkPermission(): Boolean =
        Build.VERSION.SDK_INT >= 37 &&
            ContextCompat.checkSelfPermission(context, LOCAL_NETWORK_PERMISSION) != PackageManager.PERMISSION_GRANTED

    fun startDiscovery() {
        if (_ui.value.discovering) return
        servers.clear()
        serviceNames.clear()
        _ui.update { it.copy(discovering = true, servers = emptyList(), message = null) }
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) {
                scope.launch {
                    _ui.update { it.copy(discovering = false) }
                }
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                scope.launch {
                    _ui.update {
                        it.copy(
                            discovering = false,
                            message = AppLocale.strings.syncDiscoveryFailedCode(errorCode)
                        )
                    }
                }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                scope.launch {
                    if (!serviceInfo.serviceType.contains(SERVICE_TYPE_PREFIX)) return@launch
                    resolve(serviceInfo)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                scope.launch {
                    val deviceId = serviceNames.remove(serviceInfo.serviceName)
                    if (deviceId != null) {
                        servers.remove(deviceId)
                        _ui.update { it.copy(servers = servers.values.toList()) }
                    }
                }
            }
        }
        discoveryListener = listener
        runCatching {
            nsd.discoverServices(SyncProtocol.ANDROID_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure {
            _ui.update { ui ->
                ui.copy(
                    discovering = false,
                    message = AppLocale.strings.syncDiscoveryFailedMessage(it.message)
                )
            }
        }
    }

    private fun handleResolved(serviceInfo: NsdServiceInfo) {
        val attributes = serviceInfo.attributes.entries.associate { it.key.lowercase() to it.value }
        val deviceId = attributes["id"]?.toString(Charsets.UTF_8).orEmpty()
        if (deviceId.isBlank()) return
        val host = serviceInfo.host?.hostAddress
            ?: if (Build.VERSION.SDK_INT >= 34) {
                serviceInfo.hostAddresses.firstOrNull()?.hostAddress
            } else {
                null
            }
        if (host.isNullOrBlank()) return
        val name = attributes["name"]?.toString(Charsets.UTF_8)
            ?.takeIf { it.isNotBlank() }
            ?: serviceInfo.serviceName
        val fingerprint = attributes["fp"]?.toString(Charsets.UTF_8).orEmpty()
        val server = DiscoveredServer(
            deviceId = deviceId,
            name = name,
            host = host,
            port = serviceInfo.port,
            fingerprint = fingerprint
        )
        servers[deviceId] = server
        serviceNames[serviceInfo.serviceName] = deviceId
        _ui.update { it.copy(servers = servers.values.toList()) }
    }

    private fun resolve(serviceInfo: NsdServiceInfo) {
        if (!resolving.add(serviceInfo.serviceName)) return
        runCatching {
            nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                    scope.launch {
                        resolving.remove(info.serviceName)
                    }
                }

                override fun onServiceResolved(info: NsdServiceInfo) {
                    scope.launch {
                        resolving.remove(info.serviceName)
                        handleResolved(info)
                    }
                }
            })
        }.onFailure {
            resolving.remove(serviceInfo.serviceName)
        }
    }

    fun stopDiscovery() {
        val listener = discoveryListener
        discoveryListener = null
        _ui.update { it.copy(discovering = false) }
        if (listener != null) runCatching { nsd.stopServiceDiscovery(listener) }
    }

    fun pair(server: DiscoveredServer, pairingCode: String) {
        if (_ui.value.pairing || _ui.value.syncing) return
        if (!store.secretsStorageAvailable) {
            _ui.update { it.copy(message = AppLocale.strings.syncSecureStorageRequired) }
            return
        }
        if (SyncCrypto.normalizeFingerprint(server.fingerprint).isBlank()) {
            _ui.update { it.copy(message = AppLocale.strings.syncFingerprintRequired) }
            return
        }
        _ui.update { it.copy(pairing = true, message = null) }
        scope.launch {
            val result = runCatching { pairAndSync(server, pairingCode.trim().replace(" ", "")) }
            result.onSuccess { state ->
                store.clientState = state
                _ui.update {
                    it.copy(
                        pairing = false,
                        syncing = false,
                        paired = true,
                        serverName = state.serverName,
                        serverFingerprint = state.serverFingerprint,
                        lastSyncAt = state.lastSyncAt,
                        message = AppLocale.strings.syncCompleted
                    )
                }
                stopDiscovery()
                scheduleBackgroundSync()
            }.onFailure { error ->
                val saved = store.clientState
                _ui.update {
                    it.copy(
                        pairing = false,
                        syncing = false,
                        paired = saved.paired,
                        serverName = saved.serverName,
                        serverFingerprint = saved.serverFingerprint,
                        lastSyncAt = saved.lastSyncAt,
                        message = error.message?.takeIf { msg -> msg.isNotBlank() }
                            ?: AppLocale.strings.serverConnectionFailed
                    )
                }
            }
        }
    }

    /** 데스크탑이 표시한 QR을 스캔한 결과로 바로 페어링한다. */
    fun pairFromQr(text: String) {
        val payload = PairQr.parse(text)
        if (payload == null) {
            _ui.update { it.copy(message = AppLocale.strings.syncQrInvalid) }
            return
        }
        pair(
            DiscoveredServer(
                deviceId = "",
                name = payload.name.ifBlank { payload.host },
                host = payload.host,
                port = payload.port,
                fingerprint = payload.fingerprint
            ),
            payload.code
        )
    }

    fun pairManual(address: String, pairingCode: String, fingerprint: String) {
        val trimmed = address.trim()
        if (trimmed.isBlank()) {
            _ui.update { it.copy(message = AppLocale.strings.syncInvalidAddress) }
            return
        }
        val host = trimmed.substringBeforeLast(':', trimmed)
        val port = trimmed.substringAfterLast(':', "").toIntOrNull() ?: SyncProtocol.DEFAULT_PORT
        if (host.isBlank()) {
            _ui.update { it.copy(message = AppLocale.strings.syncInvalidAddress) }
            return
        }
        if (!store.secretsStorageAvailable) {
            _ui.update { it.copy(message = AppLocale.strings.syncSecureStorageRequired) }
            return
        }
        val pin = SyncCrypto.normalizeFingerprint(fingerprint)
        if (pin.isBlank()) {
            _ui.update { it.copy(message = AppLocale.strings.syncFingerprintRequired) }
            return
        }
        pair(
            DiscoveredServer(
                deviceId = "",
                name = host,
                host = host,
                port = port,
                fingerprint = pin
            ),
            pairingCode
        )
    }

    private suspend fun pairAndSync(server: DiscoveredServer, pairingCode: String): ClientSyncState =
        syncMutex.withLock { pairAndSyncUnlocked(server, pairingCode) }

    private suspend fun pairAndSyncUnlocked(server: DiscoveredServer, pairingCode: String): ClientSyncState {
        val existing = store.clientState
        val trustManagers = mutableListOf<FingerprintTrustManager>()
        val pin = SyncCrypto.normalizeFingerprint(server.fingerprint)
        require(pin.isNotBlank()) { AppLocale.strings.syncFingerprintRequired }
        val sslContext = SyncCrypto.clientSslContext(pin, trustManagers)
        return try {
            SyncClient(sslContext, server.host, server.port).withSession { session ->
                session.send(
                    SyncHello(
                        deviceId = store.deviceId,
                        deviceName = store.deviceName,
                        clientToken = existing.clientToken
                    )
                )
                val ack = session.receive() as? SyncHelloAck
                    ?: error(AppLocale.strings.serverConnectionFailed)
                var token = ""
                if (ack.needsCode) {
                    session.send(SyncPairRequest(store.deviceId, store.deviceName, pairingCode))
                    val result = session.receive() as? SyncPairResult
                        ?: error(AppLocale.strings.serverConnectionFailed)
                    if (!result.paired) {
                        error(result.message.ifBlank { AppLocale.strings.connectionFailed })
                    }
                    token = result.clientToken
                } else if (!ack.paired) {
                    error(AppLocale.strings.connectionFailed)
                }
                val observed = trustManagers.firstOrNull()?.observed
                    ?: error(AppLocale.strings.sslFailed)
                if (observed != pin) {
                    error(AppLocale.strings.sslFailed)
                }
                val fingerprint = observed
                val sameServer = existing.paired && existing.serverDeviceId == ack.serverDeviceId
                val state = if (sameServer) {
                    existing.copy(
                        serverName = ack.serverDeviceName,
                        serverFingerprint = fingerprint,
                        serverHost = server.host,
                        serverPort = server.port,
                        clientToken = token.ifBlank { existing.clientToken }
                    )
                } else {
                    ClientSyncState(
                        serverDeviceId = ack.serverDeviceId,
                        serverName = ack.serverDeviceName,
                        serverFingerprint = fingerprint,
                        serverHost = server.host,
                        serverPort = server.port,
                        paired = true,
                        clientToken = token
                    )
                }
                // 페어링 직후 저장해, 동기화 도중 실패해도 다음 실행에서 이어갈 수 있게 한다.
                store.clientState = state
                exchangeRecords(session, state)
            }
        } catch (e: ResumeUnavailableException) {
            val cleared = withoutResume(store.clientState)
            store.clientState = cleared
            openPairedExchange(cleared, pin, retry = false)
        }
    }

    private suspend fun exchangeRecords(session: SyncSession, state: ClientSyncState): ClientSyncState {
        val now = System.currentTimeMillis()
        val export = controller.collectLocalChanges(state.index, now, state.resumeDeleteStamps)
        val fingerprint = SyncTransfer.recordsFingerprint(export.records)
        val chunks = SyncTransfer.split(export.records)
        val chunkCount = chunks.size
        val resume = canResume(state, fingerprint, chunkCount)
        val transferId = if (resume) state.resumeTransferId else SyncTransfer.newTransferId()
        val start = if (resume) state.resumePushIndex else 0
        try {
            SyncTransfer.sendPushChunks(
                session,
                transferId,
                state.lastServerVersion,
                store.deviceId,
                chunks,
                start
            ) { nextIndex ->
                store.clientState = state.copy(
                    resumeTransferId = transferId,
                    resumePushIndex = nextIndex,
                    resumeBaseVersion = state.lastServerVersion,
                    resumeRecordsHash = fingerprint,
                    resumeDeleteStamps = export.deletionTimestamps
                )
            }
        } catch (e: ResumeUnavailableException) {
            store.clientState = withoutResume(state)
            throw e
        }
        // 푸시가 끝난 뒤에는 재개 지점을 지운다. 변경분 수신이 끊겨도 다음 연결에서 0번부터 보낸다.
        store.clientState = withoutResume(store.clientState)
        val (version, records) = SyncTransfer.receiveChanges(session)
        val next = controller.applyClientRemote(state, records, version, now)
        return withoutResume(next)
    }

    private fun canResume(state: ClientSyncState, recordsHash: String, chunkCount: Int): Boolean =
        state.resumeTransferId.isNotBlank() &&
            state.resumeBaseVersion == state.lastServerVersion &&
            state.resumeRecordsHash == recordsHash &&
            state.resumePushIndex in 1 until chunkCount

    private fun withoutResume(state: ClientSyncState): ClientSyncState = state.copy(
        resumeTransferId = "",
        resumePushIndex = 0,
        resumeBaseVersion = -1,
        resumeRecordsHash = "",
        resumeDeleteStamps = emptyMap()
    )

    fun syncNow() {
        if (_ui.value.syncing) return
        _ui.update { it.copy(syncing = true, message = null) }
        scope.launch {
            val result = syncNowInternal()
            _ui.update {
                it.copy(
                    syncing = false,
                    message = result.exceptionOrNull()?.message ?: AppLocale.strings.syncCompleted
                )
            }
        }
    }

    suspend fun syncNowInternal(): Result<Unit> = syncMutex.withLock { syncNowUnlocked() }

    private suspend fun syncNowUnlocked(): Result<Unit> {
        val state = store.clientState
        if (!state.paired || state.serverHost.isBlank()) {
            return Result.failure(IllegalStateException(AppLocale.strings.syncNotPaired))
        }
        val pin = SyncCrypto.normalizeFingerprint(state.serverFingerprint)
        if (pin.isBlank()) {
            return Result.failure(IllegalStateException(AppLocale.strings.syncFingerprintRequired))
        }
        return runCatching {
            val next = openPairedExchange(state, pin)
            store.clientState = next
            _ui.update {
                it.copy(
                    paired = true,
                    serverName = next.serverName,
                    serverFingerprint = next.serverFingerprint,
                    lastSyncAt = next.lastSyncAt,
                    message = AppLocale.strings.syncCompleted
                )
            }
        }
    }

    private suspend fun openPairedExchange(
        state: ClientSyncState,
        pin: String,
        retry: Boolean = true
    ): ClientSyncState {
        suspend fun once(current: ClientSyncState): ClientSyncState {
            val trustManagers = mutableListOf<FingerprintTrustManager>()
            val sslContext = SyncCrypto.clientSslContext(pin, trustManagers)
            return SyncClient(sslContext, current.serverHost, current.serverPort).withSession { session ->
                session.send(
                    SyncHello(
                        deviceId = store.deviceId,
                        deviceName = store.deviceName,
                        clientToken = current.clientToken
                    )
                )
                val ack = session.receive() as? SyncHelloAck
                    ?: error(AppLocale.strings.serverConnectionFailed)
                if (!ack.paired || ack.needsCode) error(AppLocale.strings.syncNotPaired)
                val observed = trustManagers.firstOrNull()?.observed
                if (observed != null && observed != pin) {
                    error(AppLocale.strings.sslFailed)
                }
                exchangeRecords(session, current)
            }
        }
        return try {
            once(state)
        } catch (e: ResumeUnavailableException) {
            if (!retry) throw e
            val cleared = withoutResume(store.clientState)
            store.clientState = cleared
            once(cleared)
        }
    }

    fun forgetServer() {
        store.clearPairing()
        cancelBackgroundSync()
        _ui.update {
            it.copy(
                paired = false,
                serverName = "",
                serverFingerprint = "",
                lastSyncAt = 0,
                message = null
            )
        }
    }

    fun resetSyncIndex() {
        store.resetSyncIndex()
    }

    private fun scheduleBackgroundSync() {
        val request = PeriodicWorkRequestBuilder<AndroidSyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun cancelBackgroundSync() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    fun dispose() {
        stopDiscovery()
        scope.cancel()
    }

    private companion object {
        const val SERVICE_TYPE_PREFIX = "_startcoach._tcp"
        const val WORK_NAME = "startcoach-p2p-sync"
        const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
    }
}
