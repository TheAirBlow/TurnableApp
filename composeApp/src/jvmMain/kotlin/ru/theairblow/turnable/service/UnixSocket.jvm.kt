package ru.theairblow.turnable.service

import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.SocketChannel

internal actual fun openUnixSocket(path: String): RawSocket {
    val channel = SocketChannel.open(UnixDomainSocketAddress.of(path))
    return RawSocket(Channels.newInputStream(channel), Channels.newOutputStream(channel), channel)
}
