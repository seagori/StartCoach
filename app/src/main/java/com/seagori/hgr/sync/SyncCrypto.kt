package com.seagori.hgr.sync

import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

object SyncCrypto {
    fun fingerprint(certificate: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun normalizeFingerprint(value: String?): String =
        value.orEmpty().lowercase().replace(":", "").replace(" ", "").trim()

    fun groupedFingerprint(value: String): String =
        normalizeFingerprint(value).chunked(4).joinToString(" ")

    fun certificateFingerprint(keyStore: KeyStore, alias: String): String? {
        val certificate = keyStore.getCertificate(alias) as? X509Certificate ?: return null
        return fingerprint(certificate)
    }

    fun clientSslContext(
        expectedFingerprint: String,
        trustManagerOut: MutableList<FingerprintTrustManager>? = null
    ): SSLContext {
        val normalized = normalizeFingerprint(expectedFingerprint)
        require(normalized.isNotBlank()) { "fingerprint pinning required" }
        val trust = FingerprintTrustManager(normalized)
        trustManagerOut?.add(trust)
        return SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trust), null)
        }
    }

    fun serverSslContext(keyStore: KeyStore, password: CharArray): SSLContext {
        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        keyManagerFactory.init(keyStore, password)
        return SSLContext.getInstance("TLS").apply {
            init(keyManagerFactory.keyManagers, null, null)
        }
    }

}

class FingerprintTrustManager(
    expectedFingerprint: String
) : X509TrustManager {
    private val expected = SyncCrypto.normalizeFingerprint(expectedFingerprint)

    init {
        require(expected.isNotBlank()) { "fingerprint pinning required" }
    }

    @Volatile
    var observed: String? = null
        private set

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        throw CertificateException("client certificates not accepted")
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val certificate = chain?.firstOrNull() ?: throw CertificateException("no server certificate")
        val actual = SyncCrypto.fingerprint(certificate)
        observed = actual
        if (expected != actual) {
            throw CertificateException("server fingerprint mismatch")
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}
