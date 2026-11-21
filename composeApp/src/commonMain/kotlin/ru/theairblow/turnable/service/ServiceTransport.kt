package ru.theairblow.turnable.service

const val SERVICE_MAGIC = "TSVC"
const val SERVICE_PROTOCOL_VERSION = 3

/** Where a Turnable service listens. */
sealed interface ServiceEndpoint {
    data class Unix(val path: String) : ServiceEndpoint
    data class Tcp(val host: String, val port: Int) : ServiceEndpoint
}

/**
 * A handshaken connection to a Turnable service that exchanges whole protobuf frames.
 * Encryption (when the service requires auth) is transparent. Calls block.
 */
interface FrameConnection {
    fun readFrame(): ByteArray
    fun writeFrame(data: ByteArray)
    fun close()
}

/** The app's ML-KEM-768 key pair (raw public key, private key seed); the service matches the public key against its allowlist. */
class ClientKeys(val publicKey: ByteArray, val privateKey: ByteArray)

/**
 * Connects to [endpoint] and performs the service handshake. When the service requires auth, [keys] prove who
 * we are, and a [serverKey] makes the handshake fail unless the service presents exactly that public key.
 */
expect fun openServiceConnection(endpoint: ServiceEndpoint, keys: ClientKeys?, serverKey: ByteArray?): FrameConnection

/** Generates an ML-KEM-768 keypair as (public key, private key seed), in the same encoding as `turnable config keygen`. */
expect fun generateServiceKeyPair(): Pair<ByteArray, ByteArray>
