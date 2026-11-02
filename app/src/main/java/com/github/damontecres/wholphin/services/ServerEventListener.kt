package com.github.damontecres.wholphin.services

import android.content.Context
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.ui.collectLatestIn
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.api.sockets.subscribe
import org.jellyfin.sdk.model.api.GeneralCommandMessage
import org.jellyfin.sdk.model.api.GeneralCommandType
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.PlayCommand
import org.jellyfin.sdk.model.api.PlayMessage
import org.jellyfin.sdk.model.api.PlayRequest
import org.jellyfin.sdk.model.api.UserUpdatedMessage
import timber.log.Timber
import javax.inject.Inject

/**
 * Listens for basic messages from the server such as messages
 */
@ActivityScoped
class ServerEventListener
    @Inject
    constructor(
        @param:ActivityContext private val context: Context,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val navigationManager: NavigationManager,
    ) : DefaultLifecycleObserver {
        private val activity = (context as AppCompatActivity)

        private var listenJob: Job? = null

        init {
            activity.lifecycle.addObserver(this)
            serverRepository.current.collectLatestIn(activity.lifecycleScope) {
                Timber.d("New user/server: %s", it)
                listenJob?.cancel()
                if (it != null) {
                    init(it.server, it.user)
                }
            }
        }

        fun init(
            server: JellyfinServer?,
            user: JellyfinUser?,
        ) {
            if (server != null && user != null && api.baseUrl != null && api.accessToken != null) {
                (context as AppCompatActivity).lifecycleScope.launchIO {
                    api.sessionApi.postCapabilities(
                        playableMediaTypes = listOf(MediaType.VIDEO),
                        supportedCommands =
                            listOf(
                                GeneralCommandType.DISPLAY_MESSAGE,
                                GeneralCommandType.SEND_STRING,
                                // Benflix: phone as the TV remote
                                GeneralCommandType.MOVE_UP,
                                GeneralCommandType.MOVE_DOWN,
                                GeneralCommandType.MOVE_LEFT,
                                GeneralCommandType.MOVE_RIGHT,
                                GeneralCommandType.SELECT,
                                GeneralCommandType.BACK,
                                GeneralCommandType.GO_HOME,
                            ),
                        supportsMediaControl = true,
                    )
                    subscribeToWebSocket()
                }
            }
        }

        fun subscribeToWebSocket() {
            Timber.v("Subscribing to WebSocket")
            listenJob?.cancel()
            listenJob =
                activity.lifecycleScope.launchDefault {
                    try {
                        // Launch multiple listeners, but stop all if one fails
                        coroutineScope {
                            api.webSocket
                                .subscribe<GeneralCommandMessage>()
                                .onEach { message ->
                                    Timber.v(
                                        "Got GeneralCommandMessage: %s",
                                        message.data?.name,
                                    )
                                    when (message.data?.name) {
                                        GeneralCommandType.MOVE_UP -> {
                                            pressKey(KeyEvent.KEYCODE_DPAD_UP)
                                        }

                                        GeneralCommandType.MOVE_DOWN -> {
                                            pressKey(KeyEvent.KEYCODE_DPAD_DOWN)
                                        }

                                        GeneralCommandType.MOVE_LEFT -> {
                                            pressKey(KeyEvent.KEYCODE_DPAD_LEFT)
                                        }

                                        GeneralCommandType.MOVE_RIGHT -> {
                                            pressKey(KeyEvent.KEYCODE_DPAD_RIGHT)
                                        }

                                        GeneralCommandType.SELECT -> {
                                            pressKey(KeyEvent.KEYCODE_DPAD_CENTER)
                                        }

                                        GeneralCommandType.BACK -> {
                                            pressKey(KeyEvent.KEYCODE_BACK)
                                        }

                                        GeneralCommandType.GO_HOME -> {
                                            withContext(WholphinDispatchers.Main) { navigationManager.goToHome() }
                                        }

                                        GeneralCommandType.DISPLAY_MESSAGE,
                                        GeneralCommandType.SEND_STRING,
                                        -> {
                                            if (message.data?.arguments?.get(BENFLIX_TYPE) != null) {
                                                // Benflix remote keyboard: type into whatever box is selected
                                                typeText(
                                                    message.data
                                                        ?.arguments
                                                        ?.get("String")
                                                        .orEmpty(),
                                                )
                                                return@onEach
                                            }
                                            val header = message.data?.arguments["Header"]
                                            val text =
                                                message.data?.arguments["Text"]
                                                    ?: message.data?.arguments["String"]
                                            val toast =
                                                listOfNotNull(header, text)
                                                    .joinToString("\n")
                                            if (toast.isNotBlank()) {
                                                showToast(context, toast, Toast.LENGTH_LONG)
                                            }
                                        }

                                        else -> {
                                            Timber.v(
                                                "Ignoring GeneralCommandMessage: %s",
                                                message.data?.name,
                                            )
                                        }
                                    }
                                }.catch { ex ->
                                    Timber.e(ex, "Error in general message websocket subscription")
                                }.launchIn(this@coroutineScope)

                            // Benflix "Play on TV": someone picked this TV from their phone
                            api.webSocket
                                .subscribe<PlayMessage>()
                                .onEach { message -> onPlayRequest(message.data) }
                                .catch { ex ->
                                    Timber.e(ex, "Error in play websocket subscription")
                                }.launchIn(this@coroutineScope)

                            api.webSocket
                                .subscribe<UserUpdatedMessage>()
                                .catch { ex ->
                                    Timber.e(ex, "Error in user updated websocket subscription")
                                }.collectLatestIn(this@coroutineScope) { msg ->
                                    Timber.v("Got updated user: %s", msg.data?.id)
                                    msg.data?.let { serverRepository.updateUserDto(it) }
                                }
                        }
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.e(ex, "Error in websocket connection")
                        if (activity.lifecycleScope.isActive) {
                            subscribeToWebSocket()
                        }
                    }
                }
        }

        /** Benflix remote: acts like the TV remote's button was pressed */
        private suspend fun pressKey(code: Int) =
            withContext(WholphinDispatchers.Main) {
                activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
                activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
            }

        private suspend fun typeText(text: String) =
            withContext(WholphinDispatchers.Main) {
                val keys = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
                if (text == BACKSPACE) {
                    activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                    activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
                    return@withContext
                }
                keys.getEvents(text.toCharArray())?.forEach { activity.dispatchKeyEvent(it) }
            }

        private suspend fun onPlayRequest(request: PlayRequest?) {
            val itemId = request?.itemIds?.firstOrNull() ?: return
            if (request.playCommand != PlayCommand.PLAY_NOW) return
            if (KidsMode.isActive) {
                // The Kids profile only plays kid-rated things, so a grown-up has to switch first
                showToast(context, "Kids is on. Switch to your profile on the TV, then try again.", Toast.LENGTH_LONG)
                return
            }
            val positionMs = (request.startPositionTicks ?: 0L) / TICKS_PER_MS
            withContext(WholphinDispatchers.Main) {
                navigationManager.navigateTo(Destination.Playback(itemId = itemId, positionMs = positionMs))
            }
        }

        override fun onResume(owner: LifecycleOwner) {
            serverRepository.current.value?.let { init(it.server, it.user) }
        }

        override fun onPause(owner: LifecycleOwner) {
            Timber.v("Cancelling WebSocket")
            listenJob?.cancel()
        }

        override fun onStop(owner: LifecycleOwner) {
            Timber.v("Cancelling WebSocket")
            listenJob?.cancel()
        }
    }

private const val TICKS_PER_MS = 10_000L

/** Marks a SEND_STRING from the Benflix phone remote (typing), not a message to show */
const val BENFLIX_TYPE = "BenflixType"

/** Sent by the phone remote's delete key */
const val BACKSPACE = "\b"
