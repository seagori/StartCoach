package com.seagori.hgr.desktop

import com.seagori.hgr.sync.SyncCrypto
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.Security
import java.util.Date
import javax.net.ssl.SSLContext

class DesktopTlsConfig(
    val sslContext: SSLContext,
    val fingerprint: String
)

object DesktopTls {
    private const val ALIAS = "startcoach"

    fun loadOrCreate(baseDir: File): DesktopTlsConfig {
        val dir = File(baseDir, "sync")
        val storeFile = File(dir, "server.p12")
        val passwordFile = File(dir, "server.pass")
        if (storeFile.exists() && passwordFile.exists()) {
            DesktopFileSecurity.restrictToOwner(dir, isDirectory = true)
            DesktopFileSecurity.restrictToOwner(storeFile)
            DesktopFileSecurity.restrictToOwner(passwordFile)
            val password = runCatching { passwordFile.readText().toCharArray() }.getOrNull()
            if (password != null) {
                try {
                    runCatching { read(storeFile, password) }
                        .getOrNull()
                        ?.let { return it }
                } finally {
                    password.fill('\u0000')
                }
            }
        }
        return create(dir, storeFile, passwordFile)
    }

    private fun read(storeFile: File, password: CharArray): DesktopTlsConfig {
        val keyStore = KeyStore.getInstance("PKCS12")
        storeFile.inputStream().use { keyStore.load(it, password) }
        val fingerprint = SyncCrypto.certificateFingerprint(keyStore, ALIAS)
            ?: error("sync certificate missing")
        return DesktopTlsConfig(
            sslContext = SyncCrypto.serverSslContext(keyStore, password),
            fingerprint = fingerprint
        )
    }

    private fun create(dir: File, storeFile: File, passwordFile: File): DesktopTlsConfig {
        Security.addProvider(BouncyCastleProvider())
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val now = Date()
        val notAfter = Date(now.time + 10L * 365 * 24 * 60 * 60 * 1000)
        val subject = X500Name("CN=StartCoach Desktop")
        val serial = BigInteger(64, SecureRandom()).abs()
        val builder = JcaX509v3CertificateBuilder(
            subject,
            serial,
            now,
            notAfter,
            subject,
            keyPair.public
        )
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(false))
        builder.addExtension(
            Extension.keyUsage,
            true,
            KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyEncipherment)
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA")
            .setProvider("BC")
            .build(keyPair.private)
        val certificate = JcaX509CertificateConverter()
            .setProvider("BC")
            .getCertificate(builder.build(signer))
        certificate.checkValidity(Date())
        certificate.verify(keyPair.public)

        val passwordText = ByteArray(24)
            .also { SecureRandom().nextBytes(it) }
            .joinToString("") { "%02x".format(it) }
        val password = passwordText.toCharArray()
        return try {
            val keyStore = KeyStore.getInstance("PKCS12")
            keyStore.load(null, null)
            keyStore.setKeyEntry(ALIAS, keyPair.private, password, arrayOf(certificate))

            dir.mkdirs()
            DesktopFileSecurity.restrictToOwner(dir, isDirectory = true)
            writeAtomic(dir, storeFile, "server-p12-") { tmp ->
                tmp.outputStream().use { keyStore.store(it, password) }
            }
            writeAtomic(dir, passwordFile, "server-pass-") { tmp ->
                tmp.writeText(passwordText)
            }

            DesktopTlsConfig(
                sslContext = SyncCrypto.serverSslContext(keyStore, password),
                fingerprint = SyncCrypto.fingerprint(certificate)
            )
        } finally {
            password.fill('\u0000')
        }
    }

    private fun writeAtomic(dir: File, target: File, prefix: String, writer: (File) -> Unit) {
        val temp = File.createTempFile(prefix, ".tmp", dir)
        DesktopFileSecurity.restrictToOwner(temp)
        try {
            writer(temp)
            runCatching {
                Files.move(
                    temp.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            }.onFailure {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            DesktopFileSecurity.restrictToOwner(target)
        } finally {
            if (temp.exists()) temp.delete()
        }
    }
}
