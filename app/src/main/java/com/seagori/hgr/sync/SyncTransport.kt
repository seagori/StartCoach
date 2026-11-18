package com.seagori.hgr.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

class SyncSession(
    private val socket: Socket
) : Closeable {
    val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
    val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))

    fun send(message: SyncMessage) = SyncCodec.writeFrame(output, message)

    fun receive(): SyncMessage = SyncCodec.readFrame(input)

    override fun close() {
        runCatching { output.flush() }
        runCatching { socket.close() }
    }
}

class SyncServer(
    private val sslContext: SSLContext,
    private val port: Int,
    private val scope: CoroutineScope,
    private val maxConnections: Int = 8,
    private val handler: suspend (SyncSession) -> Unit
) {
    @Volatile
    var running: Boolean = false
        private set

    private var serverSocket: SSLServerSocket? = null
    private var acceptJob: Job? = null
    private val connectionSemaphore by lazy { Semaphore(maxConnections) }

    fun start() {
        if (running) return
        val socket = sslContext.serverSocketFactory.createServerSocket(port) as SSLServerSocket
        socket.needClientAuth = false
        socket.soTimeout = 2_000
        serverSocket = socket
        running = true
        acceptJob = scope.launch(Dispatchers.IO) {
            while (isActive && running) {
                val client = runCatching { socket.accept() }.getOrNull() ?: continue
                if (!connectionSemaphore.tryAcquire()) {
                    runCatching { client.close() }
                    continue
                }
                launch {
                    try {
                        runCatching {
                            client.soTimeout = 20_000
                            client.use { connected -> handler(SyncSession(connected)) }
                        }.onFailure { /* session failures are reported to the peer; avoid logging details */ }
                    } finally {
                        connectionSemaphore.release()
                    }
                }
            }
        }
    }

    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
        serverSocket = null
        acceptJob?.cancel()
        acceptJob = null
    }
}

class SyncClient(
    private val sslContext: SSLContext,
    private val host: String,
    private val port: Int
) {
    suspend fun <T> withSession(block: suspend (SyncSession) -> T): T = withContext(Dispatchers.IO) {
        val raw = sslContext.socketFactory.createSocket(host, port)
        val socket = raw as SSLSocket
        socket.soTimeout = 20_000
        socket.sslParameters = socket.sslParameters.apply {
            // IP 직접 연결 + fingerprint 핀닝: 호스트명 검증은 끈다.
            endpointIdentificationAlgorithm = ""
        }
        try {
            socket.startHandshake()
            SyncSession(socket).use { session -> block(session) }
        } finally {
            runCatching { socket.close() }
        }
    }
}
