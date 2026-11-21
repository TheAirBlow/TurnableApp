@file:OptIn(ExperimentalSerializationApi::class)
// BouncyCastle deprecates the low-level ML-KEM API in 1.85.2 and removes it in 1.86;
// seed-based key generation has no public replacement
@file:Suppress("DEPRECATION")

package ru.theairblow.turnable.service

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.bouncycastle.pqc.crypto.mlkem.MLKEMExtractor
import org.bouncycastle.pqc.crypto.mlkem.MLKEMGenerator
import org.bouncycastle.pqc.crypto.mlkem.MLKEMKeyGenerationParameters
import org.bouncycastle.pqc.crypto.mlkem.MLKEMKeyPairGenerator
import org.bouncycastle.pqc.crypto.mlkem.MLKEMParameters
import org.bouncycastle.pqc.crypto.mlkem.MLKEMPrivateKeyParameters
import org.bouncycastle.pqc.crypto.mlkem.MLKEMPublicKeyParameters
import ru.theairblow.turnable.proto.ClientHello
import ru.theairblow.turnable.proto.ServerAuth
import ru.theairblow.turnable.proto.ServerHello
import java.io.Closeable
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// Wire format (see turnable/pkg/service/conn.go and encryption.go):
//   plain frame:     [u32 BE length][protobuf]
//   encrypted chunk: [u32 BE length][u64 BE seq][AES-256-GCM(plain frame bytes), 12-byte tag]
// Handshake: ServerHello, ClientHello, ServerAuth (all plain), then both sides send "TSVC-OK" as the first
// encrypted chunk. The session key is clientSecret || serverSecret, each encapsulated to the other side's key.

private const val MAX_FRAME = 8 * 1024 * 1024
private const val MAX_HANDSHAKE_FRAME = 16 * 1024
private const val KEY_CONFIRMATION = "TSVC-OK"
private const val CONNECT_TIMEOUT_MS = 10_000
private const val GCM_TAG_BITS = 96

private val handshakeProto = ProtoBuf

/** Opens a raw Unix domain socket stream; implemented per platform. */
internal expect fun openUnixSocket(path: String): RawSocket

internal class RawSocket(val input: InputStream, val output: OutputStream, private val closeable: Closeable) : Closeable {
    override fun close() = closeable.close()
}

actual fun openServiceConnection(endpoint: ServiceEndpoint, keys: ClientKeys?, serverKey: ByteArray?): FrameConnection {
    val raw = when (endpoint) {
        is ServiceEndpoint.Unix -> openUnixSocket(endpoint.path)
        is ServiceEndpoint.Tcp -> Socket().run {
            try {
                connect(InetSocketAddress(endpoint.host, endpoint.port), CONNECT_TIMEOUT_MS)
            } catch (e: Throwable) {
                close()
                throw e
            }
            tcpNoDelay = true
            RawSocket(getInputStream(), getOutputStream(), this)
        }
    }
    try {
        val plain = PlainFrameConnection(raw)
        val hello = handshakeProto.decodeFromByteArray<ServerHello>(plain.readFrame(MAX_HANDSHAKE_FRAME))
        check(hello.magic == SERVICE_MAGIC) { "Not a Turnable service (bad magic \"${hello.magic}\")" }
        check(hello.version == SERVICE_PROTOCOL_VERSION) {
            "Service protocol v${hello.version} is not supported (this app speaks v$SERVICE_PROTOCOL_VERSION)"
        }
        if (!hello.authRequired) {
            check(serverKey == null || serverKey.isEmpty()) { "The service does not use encryption, but a server key is pinned" }
            return plain
        }

        require(keys != null && keys.publicKey.isNotEmpty() && keys.privateKey.isNotEmpty()) {
            "Service requires authentication, but no client key pair is configured"
        }
        check(serverKey == null || serverKey.isEmpty() || serverKey.contentEquals(hello.publicKey)) {
            "The service's public key does not match the pinned key"
        }
        val clientSecret = MLKEMGenerator(SecureRandom())
            .generateEncapsulated(MLKEMPublicKeyParameters(MLKEMParameters.ml_kem_768, hello.publicKey))
        plain.writeFrame(handshakeProto.encodeToByteArray(ClientHello(clientSecret.encapsulation, keys.publicKey)))

        val auth = try {
            handshakeProto.decodeFromByteArray<ServerAuth>(plain.readFrame(MAX_HANDSHAKE_FRAME))
        } catch (e: EOFException) {
            throw IllegalStateException("The service closed the connection. Is this app's key in its allowed_keys?", e)
        }
        val serverSecret = MLKEMExtractor(privateKeyFromSeed(keys.privateKey)).extractSecret(auth.ciphertext)
        return EncryptedFrameConnection(raw, clientSecret.secret + serverSecret)
    } catch (e: Throwable) {
        raw.close()
        throw e
    }
}

/** Rebuilds the decapsulation key from the 64-byte seed (d || z) Go's `NewDecapsulationKey768` takes. */
private fun privateKeyFromSeed(seed: ByteArray): MLKEMPrivateKeyParameters {
    require(seed.size == 64) { "Client private key must be a 64-byte seed" }
    return MLKEMPrivateKeyParameters(MLKEMParameters.ml_kem_768, seed)
}

actual fun generateServiceKeyPair(): Pair<ByteArray, ByteArray> {
    val gen = MLKEMKeyPairGenerator()
    gen.init(MLKEMKeyGenerationParameters(SecureRandom(), MLKEMParameters.ml_kem_768))
    val kp = gen.generateKeyPair()
    val pub = (kp.public as MLKEMPublicKeyParameters).encoded
    // Go's mlkem.NewDecapsulationKey768 takes the 64-byte seed form
    val priv = (kp.private as MLKEMPrivateKeyParameters).seed
    return pub to priv
}

// ─── Framing ──────────────────────────────────────────────────────────────────

private fun DataInputStream.readChunk(what: String, limit: Int = MAX_FRAME): ByteArray {
    val size = try { readInt() } catch (e: EOFException) { throw EOFException("Service closed the connection") }
    require(size in 0..limit) { "$what too large: $size bytes" }
    return ByteArray(size).also { readFully(it) }
}

private fun lengthPrefixed(data: ByteArray): ByteArray =
    ByteBuffer.allocate(4 + data.size).putInt(data.size).put(data).array()

private class PlainFrameConnection(private val raw: RawSocket) : FrameConnection {
    private val input = DataInputStream(raw.input.buffered())

    override fun readFrame(): ByteArray = readFrame(MAX_FRAME)

    fun readFrame(limit: Int): ByteArray = input.readChunk("Frame", limit)

    override fun writeFrame(data: ByteArray) {
        raw.output.write(lengthPrefixed(data))
        raw.output.flush()
    }

    override fun close() = raw.close()
}

/** Mirrors Go's encryptedConn; each frame is sent as one encrypted chunk, which Go reassembles via io.ReadFull. */
private class EncryptedFrameConnection(private val raw: RawSocket, sharedKey: ByteArray) : FrameConnection {
    private val input = DataInputStream(raw.input.buffered())
    private val write = Direction(sharedKey, "client->server")
    private val read = Direction(sharedKey, "server->client")
    private var pending = ByteArray(0)
    private var pendingPos = 0

    init {
        // Both sides prove they derived the same keys before any real frame flows
        raw.output.write(lengthPrefixed(write.seal(KEY_CONFIRMATION.toByteArray())))
        raw.output.flush()
        check(String(read.open(input.readChunk("Key confirmation", MAX_HANDSHAKE_FRAME))) == KEY_CONFIRMATION) {
            "Key confirmation mismatch"
        }
    }

    override fun readFrame(): ByteArray {
        val size = ByteBuffer.wrap(readPlain(4)).int
        require(size in 0..MAX_FRAME) { "Frame too large: $size bytes" }
        return readPlain(size)
    }

    override fun writeFrame(data: ByteArray) {
        raw.output.write(lengthPrefixed(write.seal(lengthPrefixed(data))))
        raw.output.flush()
    }

    override fun close() = raw.close()

    private fun readPlain(n: Int): ByteArray {
        val out = ByteArray(n)
        var off = 0
        while (off < n) {
            if (pendingPos >= pending.size) {
                pending = read.open(input.readChunk("Encrypted chunk"))
                pendingPos = 0
            }
            val take = minOf(n - off, pending.size - pendingPos)
            pending.copyInto(out, off, pendingPos, pendingPos + take)
            pendingPos += take
            off += take
        }
        return out
    }

    /** One direction of the tunnel: key and nonce prefix from connection.DeriveTunnelMaterial, plus a strict sequence. */
    private class Direction(sharedKey: ByteArray, label: String) {
        private val key = SecretKeySpec(sha256(sharedKey, " key $label"), "AES")
        private val prefix = sha256(sharedKey, " nonce $label").copyOf(4)
        private var seq = 0L

        fun seal(plain: ByteArray): ByteArray {
            val seqBytes = ByteBuffer.allocate(8).putLong(seq++).array()
            return seqBytes + cipher(Cipher.ENCRYPT_MODE, seqBytes).doFinal(plain)
        }

        fun open(data: ByteArray): ByteArray {
            require(data.size >= 8 + GCM_TAG_BITS / 8) { "Encrypted payload too short (${data.size} bytes)" }
            val got = ByteBuffer.wrap(data, 0, 8).long
            check(got == seq) { "Unexpected sequence number $got (want $seq)" }
            val plain = cipher(Cipher.DECRYPT_MODE, data.copyOf(8)).doFinal(data, 8, data.size - 8)
            seq++
            return plain
        }

        private fun cipher(mode: Int, seqBytes: ByteArray): Cipher =
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(mode, key, GCMParameterSpec(GCM_TAG_BITS, prefix + seqBytes))
            }

        private fun sha256(sharedKey: ByteArray, suffix: String): ByteArray =
            MessageDigest.getInstance("SHA-256").digest(sharedKey + suffix.toByteArray(Charsets.UTF_8))
    }
}
