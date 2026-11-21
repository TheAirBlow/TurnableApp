@file:OptIn(ExperimentalSerializationApi::class)

package ru.theairblow.turnable.proto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlinx.serialization.protobuf.ProtoOneOf

// Mirrors turnable/pkg/service/proto/service.proto (service protocol v3).
// Field numbers MUST match the .proto file exactly.

// ─── Enums ────────────────────────────────────────────────────────────────────

@Serializable
enum class InstanceType {
    @ProtoNumber(0) INSTANCE_TYPE_UNSPECIFIED,
    @ProtoNumber(1) INSTANCE_TYPE_SERVER,
    @ProtoNumber(2) INSTANCE_TYPE_CLIENT,
}

@Serializable
enum class InstanceEventType {
    @ProtoNumber(0) INSTANCE_EVENT_TYPE_UNSPECIFIED,
    @ProtoNumber(1) INSTANCE_EVENT_TYPE_CREATED,
    @ProtoNumber(2) INSTANCE_EVENT_TYPE_FAILED,
    @ProtoNumber(3) INSTANCE_EVENT_TYPE_STARTED,
    @ProtoNumber(4) INSTANCE_EVENT_TYPE_STOPPED,
    @ProtoNumber(5) INSTANCE_EVENT_TYPE_UPDATED,
}

@Serializable
enum class InstanceStatus {
    @ProtoNumber(0) INSTANCE_STATUS_UNSPECIFIED,
    @ProtoNumber(1) INSTANCE_STATUS_STARTING,
    @ProtoNumber(2) INSTANCE_STATUS_STARTED,
    @ProtoNumber(3) INSTANCE_STATUS_STOPPED,
    @ProtoNumber(4) INSTANCE_STATUS_FAILED,
}

@Serializable
enum class SessionEventType {
    @ProtoNumber(0) SESSION_EVENT_TYPE_UNSPECIFIED,
    @ProtoNumber(1) SESSION_EVENT_TYPE_CONNECT,
    @ProtoNumber(2) SESSION_EVENT_TYPE_DISCONNECT,
}

// ─── Shared messages ──────────────────────────────────────────────────────────

@Serializable
data class InstanceInfo(
    @ProtoNumber(1) val id: String = "",
    @ProtoNumber(2) val type: InstanceType = InstanceType.INSTANCE_TYPE_UNSPECIFIED,
    @ProtoNumber(3) val status: InstanceStatus = InstanceStatus.INSTANCE_STATUS_UNSPECIFIED,
    @ProtoNumber(4) val name: String = "",
    @ProtoNumber(5) val autostart: Boolean = false,
)

@Serializable
data class InstanceEvent(
    @ProtoNumber(1) val instanceId: String = "",
    @ProtoNumber(2) val name: String = "",
    @ProtoNumber(3) val instanceType: InstanceType = InstanceType.INSTANCE_TYPE_UNSPECIFIED,
    @ProtoNumber(4) val eventType: InstanceEventType = InstanceEventType.INSTANCE_EVENT_TYPE_UNSPECIFIED,
)

@Serializable
data class LogAttr(@ProtoNumber(1) val key: String = "", @ProtoNumber(2) val value: String = "")

@Serializable
data class LogRecord(
    @ProtoNumber(1) val time: Long = 0L, // unix nanoseconds
    @ProtoNumber(2) val level: Int = 0,  // slog level: -4 debug, 0 info, 4 warn, 8 error
    @ProtoNumber(3) val message: String = "",
    @ProtoNumber(4) val attrs: List<LogAttr> = emptyList(),
)

@Serializable
class ServerHello(
    @ProtoNumber(1) val magic: String = "",
    @ProtoNumber(2) val version: Int = 0,
    @ProtoNumber(3) val authRequired: Boolean = false,
    @ProtoNumber(4) val publicKey: ByteArray = ByteArray(0),
)

@Serializable
class ClientHello(
    @ProtoNumber(1) val ciphertext: ByteArray = ByteArray(0),
    @ProtoNumber(2) val publicKey: ByteArray = ByteArray(0),
)

@Serializable
class ServerAuth(@ProtoNumber(1) val ciphertext: ByteArray = ByteArray(0))

@Serializable
data class ProviderInfo(
    @ProtoNumber(1) val uuid: String = "",
    @ProtoNumber(2) val name: String = "",
    @ProtoNumber(3) val config: String = "",
    @ProtoNumber(4) val inUse: Boolean = false,
)

@Serializable
data class RouteInfo(
    @ProtoNumber(1) val id: String = "",
    @ProtoNumber(2) val address: String = "",
    @ProtoNumber(3) val port: Int = 0,
    @ProtoNumber(4) val socket: String = "",
    @ProtoNumber(5) val transport: String = "",
    @ProtoNumber(6) val encryption: String = "",
    @ProtoNumber(7) val name: String = "",
)

@Serializable
data class UserInfo(
    @ProtoNumber(1) val uuid: String = "",
    @ProtoNumber(2) val allowedRoutes: List<String> = emptyList(),
    @ProtoNumber(3) val type: String = "",
    @ProtoNumber(4) val forceturn: Boolean = false,
    @ProtoNumber(5) val peers: Int = 0,
)

/** Traffic in bytes from the server's point of view. */
@Serializable
data class Usage(@ProtoNumber(1) val up: Long = 0L, @ProtoNumber(2) val down: Long = 0L)

@Serializable
data class UserStats(
    @ProtoNumber(1) val uuid: String = "",
    @ProtoNumber(2) val usage: Usage? = null,
    @ProtoNumber(3) val channels: Int = 0,
    @ProtoNumber(4) val sessions: Int = 0,
    @ProtoNumber(5) val ips: List<String> = emptyList(),
    @ProtoNumber(6) val online: Boolean = false,
    @ProtoNumber(7) val lastSeen: Long = 0L, // unix seconds, 0 if never
)

@Serializable
data class SessionStats(
    @ProtoNumber(1) val id: String = "",
    @ProtoNumber(2) val userUuid: String = "",
    @ProtoNumber(3) val routeId: String = "",
    @ProtoNumber(4) val addr: String = "",
    @ProtoNumber(5) val since: Long = 0L, // unix seconds
    @ProtoNumber(6) val usage: Usage? = null,
    @ProtoNumber(7) val channels: Int = 0,
)

@Serializable
data class ServerTotals(
    @ProtoNumber(1) val started: Long = 0L, // unix seconds
    @ProtoNumber(2) val usage: Usage? = null,
    @ProtoNumber(3) val channels: Int = 0,
    @ProtoNumber(4) val sessions: Int = 0,
    @ProtoNumber(5) val users: Int = 0,
)

@Serializable
data class SessionEvent(
    @ProtoNumber(1) val instanceId: String = "",
    @ProtoNumber(2) val eventType: SessionEventType = SessionEventType.SESSION_EVENT_TYPE_UNSPECIFIED,
    @ProtoNumber(3) val userUuid: String = "",
    @ProtoNumber(4) val sessionId: String = "",
    @ProtoNumber(5) val routeId: String = "",
    @ProtoNumber(6) val addr: String = "",
    @ProtoNumber(7) val time: Long = 0L, // unix seconds
    @ProtoNumber(8) val usage: Usage? = null,
    @ProtoNumber(9) val online: Int = 0,
)

// ─── Requests ─────────────────────────────────────────────────────────────────

@Serializable data class StartServerRequest(
    @ProtoNumber(1) val config: String = "",
    @ProtoNumber(2) val instanceId: String = "",
    @ProtoNumber(3) val name: String = "",
    @ProtoNumber(4) val autostart: Boolean = false,
    @ProtoNumber(5) val providerUuid: String = "",
)
@Serializable data class StartClientRequest(
    @ProtoNumber(1) val config: String = "",
    @ProtoNumber(2) val listenAddrs: List<String> = emptyList(),
    @ProtoNumber(3) val instanceId: String = "",
    @ProtoNumber(4) val name: String = "",
    @ProtoNumber(5) val autostart: Boolean = false,
)
/** With [keep] the instance stays listed as stopped instead of being removed with its saved config. */
@Serializable data class StopInstanceRequest(@ProtoNumber(1) val instanceId: String = "", @ProtoNumber(2) val keep: Boolean = false)
@Serializable data class RestartInstanceRequest(@ProtoNumber(1) val instanceId: String = "")
/** Unset fields are left alone; a changed config or listen address list restarts the instance. */
@Serializable data class UpdateInstanceRequest(
    @ProtoNumber(1) val instanceId: String = "",
    @ProtoNumber(2) val name: String? = null,
    @ProtoNumber(3) val autostart: Boolean? = null,
    @ProtoNumber(4) val config: String? = null,
    @ProtoNumber(5) val listenAddrs: List<String> = emptyList(), // clients only, replaces the list when not empty
)
@Serializable data class UpdateProviderRequest(
    @ProtoNumber(1) val uuid: String = "",
    @ProtoNumber(2) val name: String? = null,
    @ProtoNumber(3) val config: String? = null,
)
@Serializable class ListInstancesRequest
@Serializable data class GetInstanceRequest(@ProtoNumber(1) val instanceId: String = "")
@Serializable data class ValidateServerConfigRequest(@ProtoNumber(1) val config: String = "")
@Serializable data class ValidateClientConfigRequest(@ProtoNumber(1) val config: String = "")
@Serializable data class ConvertClientConfigRequest(@ProtoNumber(1) val config: String = "")
@Serializable data class AddProviderRequest(
    @ProtoNumber(1) val uuid: String = "",
    @ProtoNumber(2) val name: String = "",
    @ProtoNumber(3) val config: String = "",
)
@Serializable data class GetProviderRequest(@ProtoNumber(1) val uuid: String = "")
@Serializable class ListProvidersRequest
@Serializable data class DeleteProviderRequest(@ProtoNumber(1) val uuid: String = "")
@Serializable data class ValidateProviderConfigRequest(@ProtoNumber(1) val config: String = "")
@Serializable data class AddProviderRouteRequest(@ProtoNumber(1) val providerUuid: String = "", @ProtoNumber(2) val route: RouteInfo? = null)
@Serializable data class DeleteProviderRouteRequest(@ProtoNumber(1) val providerUuid: String = "", @ProtoNumber(2) val routeId: String = "")
@Serializable data class ListProviderRoutesRequest(@ProtoNumber(1) val providerUuid: String = "")
@Serializable data class AddProviderUserRequest(@ProtoNumber(1) val providerUuid: String = "", @ProtoNumber(2) val user: UserInfo? = null)
@Serializable data class DeleteProviderUserRequest(@ProtoNumber(1) val providerUuid: String = "", @ProtoNumber(2) val userUuid: String = "")
@Serializable data class ListProviderUsersRequest(@ProtoNumber(1) val providerUuid: String = "")
@Serializable data class GetStatsRequest(
    @ProtoNumber(1) val instanceId: String = "",
    @ProtoNumber(2) val collect: Boolean = false,
    @ProtoNumber(3) val ackToken: Long? = null,
)
@Serializable data class ListSessionsRequest(@ProtoNumber(1) val instanceId: String = "")
/** Set exactly one of [userUuid] / [sessionId]. */
@Serializable data class KickRequest(
    @ProtoNumber(1) val instanceId: String = "",
    @ProtoNumber(2) val userUuid: String = "",
    @ProtoNumber(3) val sessionId: String = "",
)

@Serializable sealed interface RequestPayload {
    @Serializable class StartServer(@ProtoNumber(1) val v: StartServerRequest) : RequestPayload
    @Serializable class StartClient(@ProtoNumber(2) val v: StartClientRequest) : RequestPayload
    @Serializable class StopInstance(@ProtoNumber(3) val v: StopInstanceRequest) : RequestPayload
    @Serializable class ListInstances(@ProtoNumber(5) val v: ListInstancesRequest) : RequestPayload
    @Serializable class ValidateServerConfig(@ProtoNumber(6) val v: ValidateServerConfigRequest) : RequestPayload
    @Serializable class ValidateClientConfig(@ProtoNumber(7) val v: ValidateClientConfigRequest) : RequestPayload
    @Serializable class ConvertClientConfig(@ProtoNumber(8) val v: ConvertClientConfigRequest) : RequestPayload
    @Serializable class GetInstance(@ProtoNumber(9) val v: GetInstanceRequest) : RequestPayload
    @Serializable class AddProvider(@ProtoNumber(15) val v: AddProviderRequest) : RequestPayload
    @Serializable class GetProvider(@ProtoNumber(16) val v: GetProviderRequest) : RequestPayload
    @Serializable class ListProviders(@ProtoNumber(17) val v: ListProvidersRequest) : RequestPayload
    @Serializable class DeleteProvider(@ProtoNumber(18) val v: DeleteProviderRequest) : RequestPayload
    @Serializable class ValidateProviderConfig(@ProtoNumber(19) val v: ValidateProviderConfigRequest) : RequestPayload
    @Serializable class AddProviderRoute(@ProtoNumber(20) val v: AddProviderRouteRequest) : RequestPayload
    @Serializable class DeleteProviderRoute(@ProtoNumber(21) val v: DeleteProviderRouteRequest) : RequestPayload
    @Serializable class ListProviderRoutes(@ProtoNumber(22) val v: ListProviderRoutesRequest) : RequestPayload
    @Serializable class AddProviderUser(@ProtoNumber(23) val v: AddProviderUserRequest) : RequestPayload
    @Serializable class DeleteProviderUser(@ProtoNumber(24) val v: DeleteProviderUserRequest) : RequestPayload
    @Serializable class ListProviderUsers(@ProtoNumber(25) val v: ListProviderUsersRequest) : RequestPayload
    @Serializable class GetStats(@ProtoNumber(26) val v: GetStatsRequest) : RequestPayload
    @Serializable class ListSessions(@ProtoNumber(27) val v: ListSessionsRequest) : RequestPayload
    @Serializable class Kick(@ProtoNumber(28) val v: KickRequest) : RequestPayload
    @Serializable class RestartInstance(@ProtoNumber(29) val v: RestartInstanceRequest) : RequestPayload
    @Serializable class UpdateInstance(@ProtoNumber(30) val v: UpdateInstanceRequest) : RequestPayload
    @Serializable class UpdateProvider(@ProtoNumber(31) val v: UpdateProviderRequest) : RequestPayload
}

/** The service copies [requestId] into its reply; pushed logs and events carry 0. */
@Serializable data class Request(
    @ProtoOneOf val payload: RequestPayload? = null,
    @ProtoNumber(100) val requestId: Long = 0L,
)

// ─── Responses ────────────────────────────────────────────────────────────────

@Serializable data class StartServerResponse(@ProtoNumber(1) val instanceId: String = "", @ProtoNumber(2) val error: String = "")
@Serializable data class StartClientResponse(@ProtoNumber(1) val instanceId: String = "", @ProtoNumber(2) val error: String = "")
@Serializable data class StopInstanceResponse(@ProtoNumber(1) val error: String = "")
@Serializable data class RestartInstanceResponse(@ProtoNumber(1) val error: String = "")
@Serializable data class UpdateInstanceResponse(@ProtoNumber(1) val error: String = "")
@Serializable data class UpdateProviderResponse(@ProtoNumber(1) val error: String = "")
@Serializable data class ListInstancesResponse(@ProtoNumber(1) val instances: List<InstanceInfo> = emptyList())
@Serializable data class ValidateServerConfigResponse(@ProtoNumber(1) val valid: Boolean = false, @ProtoNumber(2) val error: String = "")
@Serializable data class ValidateClientConfigResponse(@ProtoNumber(1) val valid: Boolean = false, @ProtoNumber(2) val error: String = "")
@Serializable data class ConvertClientConfigResponse(@ProtoNumber(1) val config: String = "", @ProtoNumber(2) val error: String = "")
@Serializable data class GetInstanceResponse(
    @ProtoNumber(1) val info: InstanceInfo? = null,
    @ProtoNumber(2) val config: String = "",
    @ProtoNumber(3) val listenAddrs: List<String> = emptyList(),
    @ProtoNumber(4) val error: String = "",
)
@Serializable data class AddProviderResponse(@ProtoNumber(1) val uuid: String = "", @ProtoNumber(2) val error: String = "")
@Serializable data class GetProviderResponse(@ProtoNumber(1) val provider: ProviderInfo? = null, @ProtoNumber(2) val error: String = "")
@Serializable data class ListProvidersResponse(@ProtoNumber(1) val providers: List<ProviderInfo> = emptyList())
@Serializable data class DeleteProviderResponse(@ProtoNumber(1) val error: String = "")
@Serializable data class ValidateProviderConfigResponse(@ProtoNumber(1) val valid: Boolean = false, @ProtoNumber(2) val error: String = "")
@Serializable data class AddProviderRouteResponse(@ProtoNumber(1) val error: String = "")
@Serializable data class DeleteProviderRouteResponse(@ProtoNumber(1) val error: String = "")
@Serializable data class ListProviderRoutesResponse(@ProtoNumber(1) val routes: List<RouteInfo> = emptyList(), @ProtoNumber(2) val error: String = "")
@Serializable data class AddProviderUserResponse(@ProtoNumber(1) val error: String = "")
@Serializable data class DeleteProviderUserResponse(@ProtoNumber(1) val error: String = "")
@Serializable data class ListProviderUsersResponse(@ProtoNumber(1) val users: List<UserInfo> = emptyList(), @ProtoNumber(2) val error: String = "")
@Serializable data class GetStatsResponse(
    @ProtoNumber(1) val totals: ServerTotals? = null,
    @ProtoNumber(2) val users: List<UserStats> = emptyList(),
    @ProtoNumber(3) val collected: Map<String, Usage> = emptyMap(),
    @ProtoNumber(4) val error: String = "",
    /** Identifies the collected batch; send it back as `ackToken` once processed. */
    @ProtoNumber(5) val collectToken: Long = 0L,
)
@Serializable data class ListSessionsResponse(@ProtoNumber(1) val sessions: List<SessionStats> = emptyList(), @ProtoNumber(2) val error: String = "")
@Serializable data class KickResponse(@ProtoNumber(1) val kicked: Int = 0, @ProtoNumber(2) val error: String = "")
@Serializable data class ErrorResponse(@ProtoNumber(1) val message: String = "")

@Serializable sealed interface ResponsePayload {
    @Serializable class StartServer(@ProtoNumber(1) val v: StartServerResponse) : ResponsePayload
    @Serializable class StartClient(@ProtoNumber(2) val v: StartClientResponse) : ResponsePayload
    @Serializable class StopInstance(@ProtoNumber(3) val v: StopInstanceResponse) : ResponsePayload
    @Serializable class ListInstances(@ProtoNumber(5) val v: ListInstancesResponse) : ResponsePayload
    @Serializable class Log(@ProtoNumber(6) val v: LogRecord) : ResponsePayload
    @Serializable class Error(@ProtoNumber(7) val v: ErrorResponse) : ResponsePayload
    @Serializable class ValidateServerConfig(@ProtoNumber(8) val v: ValidateServerConfigResponse) : ResponsePayload
    @Serializable class ValidateClientConfig(@ProtoNumber(9) val v: ValidateClientConfigResponse) : ResponsePayload
    @Serializable class ConvertClientConfig(@ProtoNumber(10) val v: ConvertClientConfigResponse) : ResponsePayload
    @Serializable class GetInstance(@ProtoNumber(11) val v: GetInstanceResponse) : ResponsePayload
    @Serializable class InstanceEventPayload(@ProtoNumber(12) val v: InstanceEvent) : ResponsePayload
    @Serializable class AddProvider(@ProtoNumber(18) val v: AddProviderResponse) : ResponsePayload
    @Serializable class GetProvider(@ProtoNumber(19) val v: GetProviderResponse) : ResponsePayload
    @Serializable class ListProviders(@ProtoNumber(20) val v: ListProvidersResponse) : ResponsePayload
    @Serializable class DeleteProvider(@ProtoNumber(21) val v: DeleteProviderResponse) : ResponsePayload
    @Serializable class ValidateProviderConfig(@ProtoNumber(22) val v: ValidateProviderConfigResponse) : ResponsePayload
    @Serializable class AddProviderRoute(@ProtoNumber(23) val v: AddProviderRouteResponse) : ResponsePayload
    @Serializable class DeleteProviderRoute(@ProtoNumber(24) val v: DeleteProviderRouteResponse) : ResponsePayload
    @Serializable class ListProviderRoutes(@ProtoNumber(25) val v: ListProviderRoutesResponse) : ResponsePayload
    @Serializable class AddProviderUser(@ProtoNumber(26) val v: AddProviderUserResponse) : ResponsePayload
    @Serializable class DeleteProviderUser(@ProtoNumber(27) val v: DeleteProviderUserResponse) : ResponsePayload
    @Serializable class ListProviderUsers(@ProtoNumber(28) val v: ListProviderUsersResponse) : ResponsePayload
    @Serializable class GetStats(@ProtoNumber(29) val v: GetStatsResponse) : ResponsePayload
    @Serializable class ListSessions(@ProtoNumber(30) val v: ListSessionsResponse) : ResponsePayload
    @Serializable class Kick(@ProtoNumber(31) val v: KickResponse) : ResponsePayload
    @Serializable class SessionEventPayload(@ProtoNumber(32) val v: SessionEvent) : ResponsePayload
    @Serializable class RestartInstance(@ProtoNumber(33) val v: RestartInstanceResponse) : ResponsePayload
    @Serializable class UpdateInstance(@ProtoNumber(34) val v: UpdateInstanceResponse) : ResponsePayload
    @Serializable class UpdateProvider(@ProtoNumber(35) val v: UpdateProviderResponse) : ResponsePayload
}

@Serializable data class Response(
    @ProtoOneOf val payload: ResponsePayload? = null,
    @ProtoNumber(100) val requestId: Long = 0L,
)
