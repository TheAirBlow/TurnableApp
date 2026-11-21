package ru.theairblow.turnable.service

import android.net.LocalSocket
import android.net.LocalSocketAddress

internal actual fun openUnixSocket(path: String): RawSocket {
    val socket = LocalSocket()
    try {
        socket.connect(LocalSocketAddress(path, LocalSocketAddress.Namespace.FILESYSTEM))
    } catch (e: Throwable) {
        socket.close()
        throw e
    }
    return RawSocket(socket.inputStream, socket.outputStream, socket)
}
