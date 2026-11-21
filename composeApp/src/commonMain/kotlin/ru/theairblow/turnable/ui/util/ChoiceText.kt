package ru.theairblow.turnable.ui.util

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import turnable.composeapp.generated.resources.*

/** Localized one-line hint for a protocol option. */
@Composable
fun describeProto(proto: String): String? = when (proto) {
    "dtls" -> stringResource(Res.string.choice_proto_dtls)
    "srtp" -> stringResource(Res.string.choice_proto_srtp)
    "none" -> stringResource(Res.string.choice_proto_none)
    else -> null
}

/** Localized one-line hint for a transport option. */
@Composable
fun describeTransport(transport: String): String? = when (transport) {
    "mpath" -> stringResource(Res.string.choice_transport_mpath)
    "kcp" -> stringResource(Res.string.choice_transport_kcp)
    "sctp" -> stringResource(Res.string.choice_transport_sctp)
    "none" -> stringResource(Res.string.choice_transport_none)
    else -> null
}
