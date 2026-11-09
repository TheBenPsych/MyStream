package com.github.damontecres.wholphin.services

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.model.api.GeneralCommand
import org.jellyfin.sdk.model.api.GeneralCommandType
import timber.log.Timber
import java.util.UUID

/** The TV that wants typing, as the phone sees it */
data class RemoteKeysRequest(
    val tvSessionId: String,
    val tvName: String,
    val text: String,
)

/**
 * Benflix "Type on your phone for the TV": when a TV's search keyboard opens, every phone with
 * Benflix open on the same account gets a keyboard pop-up. What's typed on the phone goes straight
 * into the TV's search box. Phones only hear it while Benflix is open on them (the live connection
 * closes when the app is in the background).
 */
object RemoteKeys {
    /** Arguments in the SEND_STRING messages */
    const val ARG = "BenflixKeys"
    private const val OPEN = "open"
    private const val CLOSE = "close"
    private const val TEXT = "text"
    private const val ARG_TEXT = "BenflixKeysText"
    private const val ARG_NAME = "BenflixKeysName"
    private const val ARG_SESSION = "BenflixKeysSession"
    private const val BENFLIX_DEVICE_PREFIX = "benflix-"

    // Phone side
    private val _request = MutableStateFlow<RemoteKeysRequest?>(null)

    /** A TV waiting for typing (phones only) */
    val request: StateFlow<RemoteKeysRequest?> = _request

    fun dismiss() {
        _request.value = null
    }

    // TV side
    private val _incoming = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** Text typed on a phone, for the open search keyboard (TVs only) */
    val incoming: SharedFlow<String> = _incoming

    /** Called for every SEND_STRING with [ARG]; true when it was one of these */
    fun onMessage(
        args: Map<String, String?>?,
        isTv: Boolean,
    ): Boolean {
        val kind = args?.get(ARG) ?: return false
        when (kind) {
            OPEN -> {
                if (!isTv) {
                    val session = args[ARG_SESSION] ?: return true
                    _request.value = RemoteKeysRequest(session, args[ARG_NAME] ?: "the TV", args[ARG_TEXT].orEmpty())
                }
            }

            CLOSE -> {
                if (!isTv && _request.value?.tvSessionId == args[ARG_SESSION]) _request.value = null
            }

            TEXT -> {
                if (isTv) _incoming.tryEmit(args[ARG_TEXT].orEmpty())
            }
        }
        return true
    }

    /** TV: tells this account's phones the search keyboard opened ([open] true) or closed */
    suspend fun announce(
        api: ApiClient,
        userId: UUID?,
        open: Boolean,
        text: String = "",
    ) {
        if (userId == null) return
        try {
            val sessions = api.sessionApi.getSessions(controllableByUserId = userId).content
            val myDevice = api.deviceInfo.id
            val me = sessions.firstOrNull { it.deviceId == myDevice } ?: return
            val mySession = me.id ?: return
            sessions
                .filter {
                    it.userId == userId &&
                        it.deviceId != myDevice &&
                        it.deviceId?.startsWith(BENFLIX_DEVICE_PREFIX) == true &&
                        it.supportsRemoteControl
                }.forEach { phone ->
                    val id = phone.id ?: return@forEach
                    try {
                        api.sessionApi.sendFullGeneralCommand(
                            id,
                            GeneralCommand(
                                name = GeneralCommandType.SEND_STRING,
                                controllingUserId = userId,
                                arguments =
                                    mapOf(
                                        ARG to if (open) OPEN else CLOSE,
                                        ARG_SESSION to mySession,
                                        ARG_NAME to (me.deviceName ?: "the TV"),
                                        ARG_TEXT to text,
                                        "String" to "",
                                    ),
                            ),
                        )
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.v(ex, "Phone keyboard: couldn't reach %s", phone.deviceName)
                    }
                }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Timber.w(ex, "Phone keyboard: couldn't look for phones")
        }
    }

    /** Phone: sends the whole text typed so far to the TV */
    suspend fun send(
        api: ApiClient,
        userId: UUID?,
        request: RemoteKeysRequest,
        text: String,
    ) {
        if (userId == null) return
        try {
            api.sessionApi.sendFullGeneralCommand(
                request.tvSessionId,
                GeneralCommand(
                    name = GeneralCommandType.SEND_STRING,
                    controllingUserId = userId,
                    arguments = mapOf(ARG to TEXT, ARG_TEXT to text, "String" to ""),
                ),
            )
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Timber.w(ex, "Phone keyboard: couldn't send to the TV")
        }
    }
}
