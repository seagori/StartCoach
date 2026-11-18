package com.seagori.hgr.sync

import kotlinx.serialization.json.Json
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

object SyncCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "kind"
    }

    fun json(): Json = json

    fun encode(message: SyncMessage): String =
        json.encodeToString(SyncMessage.serializer(), message)

    fun decode(text: String): SyncMessage =
        json.decodeFromString(SyncMessage.serializer(), text)

    fun writeFrame(output: OutputStream, message: SyncMessage) {
        val bytes = encode(message).toByteArray(Charsets.UTF_8)
        require(bytes.size <= SyncProtocol.MAX_FRAME_BYTES) { "sync frame too large: ${bytes.size}" }
        val out = if (output is DataOutputStream) output else DataOutputStream(output)
        out.writeInt(bytes.size)
        out.write(bytes)
        out.flush()
    }

    fun readFrame(input: InputStream): SyncMessage {
        val data = if (input is DataInputStream) input else DataInputStream(input)
        val size = data.readInt()
        require(size in 1..SyncProtocol.MAX_FRAME_BYTES) { "invalid sync frame size: $size" }
        val bytes = ByteArray(size)
        data.readFully(bytes)
        return decode(bytes.decodeToString())
    }

    fun sha256(text: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
