package com.seagori.hgr.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.seagori.hgr.data.settings.SettingsStore
import com.seagori.hgr.i18n.AppLocale
import com.seagori.hgr.sync.PairQr
import com.seagori.hgr.sync.ServerSyncState
import com.seagori.hgr.sync.SyncController
import com.seagori.hgr.sync.SyncError
import com.seagori.hgr.sync.SyncHello
import com.seagori.hgr.sync.SyncHelloAck
import com.seagori.hgr.sync.SyncPairedDevice
import com.seagori.hgr.sync.SyncPairRequest
import com.seagori.hgr.sync.SyncPairResult
import com.seagori.hgr.sync.SyncProtocol
import com.seagori.hgr.sync.SyncRecord
import com.seagori.hgr.sync.SyncServer
import com.seagori.hgr.sync.SyncSession
import com.seagori.hgr.sync.SyncStore
import com.seagori.hgr.sync.SyncChunkBuffer
import com.seagori.hgr.sync.SyncTransfer
import com.seagori.hgr.sync.SyncTypes
import com.seagori.hgr.sync.toRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

class DesktopSyncManager(
    private val scope: CoroutineScope,
    private val baseDir: File,
    private val deviceId: String,
    private val deviceName: String,
    settings: SettingsStore,
    readData: suspend () -> DesktopData,
    writeData: suspend ((DesktopData) -> DesktopData) -> DesktopData,
    onSettingsChanged: suspend () -> Unit = {}
) {
    val port: Int = SyncProtocol.DEFAULT_PORT
    private val store: SyncStore = DesktopSyncStore(
        deviceId = deviceId,
        readData = readData,
        writeData = writeData,
        settings = settings,
        onSettingsChanged = onSettingsChanged
    )
    private val controller = SyncController(store)
    private val stateStore = DesktopSyncStateStore(baseDir)
    private val advertiser = DesktopSyncAdvertiser()
    private val mutex = Mutex()
    private val pushBuffers: MutableMap<String, SyncChunkBuffer> = ConcurrentHashMap()
    private var server: SyncServer? = null
    private var serverState: ServerSyncState = stateStore.load()
    private var failedPairingAttempts = 0

    var running by mutableStateOf(false)
        private set
    var pairingCode by mutableStateOf("")
        private set
    var fingerprint by mutableStateOf("")
        private set
    var pairedDevices by mutableStateOf(serverState.pairedDevices)
        private set
    var lastSyncAt by mutableStateOf(
        serverState.pairedDevices.mapNotNull { it.lastSyncAt }.maxOrNull() ?: 0L
    )
        private set
    var lastMessage by mutableStateOf<String?>(null)

    /** QR에 넣을 같은 Wi-Fi의 IPv4 주소. 없으면 빈 문자열. */
    var lanHost by mutableStateOf("")
        private set

    fun pairQrText(): String? {
        if (!running || pairingCode.isBlank() || fingerprint.isBlank()) return null
        val host = lanHost.ifBlank { return null }
        return PairQr.encode(host, port, fingerprint, pairingCode, deviceName)
    }

    fun start() {
        if (running) return
        serverState = stateStore.load()
        pairedDevices = serverState.pairedDevices
        val tls = runCatching { DesktopTls.loadOrCreate(baseDir) }.getOrElse {
            lastMessage = AppLocale.strings.syncCertPrepareFailed(it.message)
            return
        }
        fingerprint = tls.fingerprint
        pairingCode = newCode()
        lanHost = detectLanHost()
        val srv = SyncServer(tls.sslContext, port, scope) { session ->
            handle(session, tls.fingerprint)
        }
        runCatching { srv.start() }.onFailure {
            lastMessage = AppLocale.strings.syncServerStartFailed(it.message)
            return
        }
        server = srv
        running = true
        runCatching {
            advertiser.start(deviceId, deviceName, port, tls.fingerprint)
        }.onFailure {
            lastMessage = AppLocale.strings.syncMdnsAdvertiseFailed
        }
        if (lastMessage == null) lastMessage = AppLocale.strings.syncServerStarted
    }

    fun stop() {
        server?.stop()
        server = null
        advertiser.stop()
        running = false
        pairingCode = ""
        lastMessage = AppLocale.strings.syncServerStopped
    }

    fun refreshPairingCode() {
        pairingCode = newCode()
        if (lanHost.isBlank()) lanHost = detectLanHost()
    }

    fun resetSyncIndex() {
        serverState = serverState.copy(version = 0L, index = emptyList(), tombstones = emptyList())
        stateStore.save(serverState)
    }

    fun dispose() {
        stop()
    }

    private suspend fun handle(session: SyncSession, serverFingerprint: String) {
        val hello = session.receive() as? SyncHello ?: run {
            session.send(SyncError("hello expected"))
            return
        }
        if (hello.protocol != SyncProtocol.VERSION) {
            session.send(SyncError("protocol mismatch"))
            return
        }
        val now = System.currentTimeMillis()
        val tokenOk = mutex.withLock {
            val device = serverState.pairedDevices.firstOrNull { it.deviceId == hello.deviceId }
            device != null && hello.clientToken.isNotBlank() &&
                MessageDigest.isEqual(
                    hello.clientToken.toByteArray(Charsets.UTF_8),
                    device.token.toByteArray(Charsets.UTF_8)
                )
        }

        if (!tokenOk) {
            session.send(
                SyncHelloAck(
                    serverDeviceId = deviceId,
                    serverDeviceName = deviceName,
                    serverFingerprint = serverFingerprint,
                    paired = false,
                    needsCode = true
                )
            )
            val pair = session.receive() as? SyncPairRequest ?: return
            val inputCode = pair.pairingCode.trim().replace(" ", "")
            val pairResultToken: String? = mutex.withLock {
                val codeMatches = pairingCode.isNotBlank() && MessageDigest.isEqual(
                    inputCode.toByteArray(Charsets.UTF_8),
                    pairingCode.toByteArray(Charsets.UTF_8)
                )
                if (!codeMatches) {
                    failedPairingAttempts++
                    if (failedPairingAttempts >= MAX_PAIRING_ATTEMPTS) {
                        pairingCode = newCode()
                        failedPairingAttempts = 0
                        lastMessage = AppLocale.strings.syncPairingCodeReset
                    } else {
                        lastMessage = AppLocale.strings.syncPairingCodeMismatchCount(
                            failedPairingAttempts,
                            MAX_PAIRING_ATTEMPTS
                        )
                    }
                    null
                } else {
                    failedPairingAttempts = 0
                    val token = randomToken()
                    val device = SyncPairedDevice(
                        deviceId = hello.deviceId,
                        name = pair.deviceName.ifBlank { hello.deviceName },
                        pairedAt = now,
                        token = token
                    )
                    serverState = serverState.copy(
                        pairedDevices = serverState.pairedDevices.filterNot { it.deviceId == hello.deviceId } + device
                    )
                    stateStore.save(serverState)
                    pairedDevices = serverState.pairedDevices
                    pairingCode = newCode()
                    token
                }
            }

            if (pairResultToken == null) {
                delay(1_000)
                session.send(SyncPairResult(false, AppLocale.strings.syncPairingCodeMismatch))
                return
            }
            session.send(SyncPairResult(true, clientToken = pairResultToken))
        } else {
            session.send(
                SyncHelloAck(
                    serverDeviceId = deviceId,
                    serverDeviceName = deviceName,
                    serverFingerprint = serverFingerprint,
                    paired = true,
                    needsCode = false
                )
            )
        }

        val first = session.receive()
        val (baseVersion, pushRecords) = runCatching {
            SyncTransfer.receivePush(session, first, pushBuffers, expectedDeviceId = hello.deviceId)
        }.getOrElse { error ->
            if (error.message != SyncTransfer.RESUME_UNAVAILABLE) {
                session.send(SyncError(error.message ?: "push chunk expected"))
            }
            return
        }

        val (responseVersion, responseRecords) = mutex.withLock {
            serverState = controller.flushServerLocal(serverState, now)
            serverState = controller.applyServerRemote(serverState, pushRecords, now)
            val response = controller.changesFor(serverState, baseVersion)

            // 클라이언트가 보낸 레코드가 서버에서 거부됐더라도 현재 서버 값을 돌려줘 수렴시킨다.
            val pushedKeys = pushRecords.map { it.syncKey }.toSet()
            val byKey = LinkedHashMap<String, SyncRecord>()
            response.records.forEach { byKey[it.syncKey] = it }
            if (pushedKeys.isNotEmpty()) {
                store.snapshot(serverState.index)
                    .filter { it.record.syncKey in pushedKeys }
                    .forEach { byKey.putIfAbsent(it.record.syncKey, it.record) }
                serverState.tombstones
                    .filter { it.syncKey in pushedKeys }
                    .forEach { byKey.putIfAbsent(it.syncKey, it.toRecord()) }
            }

            serverState = serverState.copy(
                pairedDevices = serverState.pairedDevices.map {
                    if (it.deviceId == hello.deviceId) it.copy(lastSyncAt = now) else it
                }
            )
            stateStore.save(serverState)
            pairedDevices = serverState.pairedDevices
            lastSyncAt = now
            lastMessage = AppLocale.strings.syncDeviceCompleted(hello.deviceName)
            response.version to byKey.values.sortedBy { SyncTypes.rank(it.type) }
        }

        SyncTransfer.sendChanges(
            session,
            SyncTransfer.newTransferId(),
            responseVersion,
            responseRecords
        )
    }

    private companion object {
        const val MAX_PAIRING_ATTEMPTS = 5
    }

    private fun newCode(): String = "%06d".format(SecureRandom().nextInt(1_000_000))

    /** 같은 LAN에서 Android가 접근할 수 있는 사설 IPv4를 고른다. */
    private fun detectLanHost(): String {
        val addresses = runCatching {
            java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())
                .filter { iface -> runCatching { iface.isUp && !iface.isLoopback }.getOrDefault(false) }
                .flatMap { iface -> java.util.Collections.list(iface.inetAddresses) }
                .filterIsInstance<java.net.Inet4Address>()
                .filter { address -> !address.isLoopbackAddress }
        }.getOrDefault(emptyList())
        return addresses.firstOrNull { it.isSiteLocalAddress }?.hostAddress
            ?: addresses.firstOrNull()?.hostAddress
            ?: ""
    }

    private fun randomToken(): String {
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
