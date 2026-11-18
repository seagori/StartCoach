package com.seagori.hgr.desktop

import com.seagori.hgr.sync.SyncProtocol
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

class DesktopSyncAdvertiser {
    private var jmdns: JmDNS? = null
    private var info: ServiceInfo? = null

    fun start(deviceId: String, deviceName: String, port: Int, fingerprint: String): Result<Unit> = runCatching {
        stop()
        val mdns = JmDNS.create()
        val serviceName = "${SyncProtocol.SERVICE_NAME}-${deviceId.take(6)}"
        val service = ServiceInfo.create(
            SyncProtocol.SERVICE_TYPE,
            serviceName,
            port,
            0,
            0,
            mapOf(
                "id" to deviceId,
                "name" to deviceName,
                "fp" to fingerprint,
                "v" to SyncProtocol.VERSION.toString()
            )
        )
        mdns.registerService(service)
        jmdns = mdns
        info = service
    }

    fun stop() {
        runCatching { info?.let { jmdns?.unregisterService(it) } }
        runCatching { jmdns?.close() }
        info = null
        jmdns = null
    }
}
