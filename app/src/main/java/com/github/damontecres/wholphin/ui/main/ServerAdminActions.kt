package com.github.damontecres.wholphin.ui.main

import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.devicesApi
import org.jellyfin.sdk.api.client.extensions.itemRefreshApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.scheduledTasksApi
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MessageCommand
import org.jellyfin.sdk.model.api.MetadataRefreshMode
import org.jellyfin.sdk.model.api.PlaystateCommand
import org.jellyfin.sdk.model.api.TaskCompletionStatus
import org.jellyfin.sdk.model.api.TaskInfo
import org.jellyfin.sdk.model.api.TaskState
import org.jellyfin.sdk.model.api.UserDto
import timber.log.Timber
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Benflix: one of Jellyfin's background chores, in plain words */
data class ServerChore(
    val id: String,
    val name: String,
    val running: Boolean,
    val progress: Int?,
    val lastRun: String?,
    val failed: Boolean,
    val error: String?,
)

/**
 * Benflix admin tools that only need Jellyfin: pause or message one stream, scan for new files,
 * the background chores, restart Jellyfin, and the People settings (screens at once, which
 * libraries they see, sign out everywhere).
 */
@Singleton
class BenflixServerActions
    @Inject
    constructor(
        private val api: ApiClient,
    ) {
        suspend fun pause(sessionId: String) = io { api.sessionApi.sendPlaystateCommand(sessionId, PlaystateCommand.PAUSE) }

        suspend fun resume(sessionId: String) = io { api.sessionApi.sendPlaystateCommand(sessionId, PlaystateCommand.UNPAUSE) }

        suspend fun message(
            sessionId: String,
            text: String,
        ) = io {
            api.sessionApi.sendMessageCommand(
                sessionId = sessionId,
                data = MessageCommand(header = "Message from Ben", text = text, timeoutMs = 15_000L),
            )
        }

        /** Sends the pop-up to every open screen, returns how many got it */
        suspend fun messageEveryone(text: String): Int =
            io {
                val sessions =
                    api.sessionApi
                        .getSessions(activeWithinSeconds = 960)
                        .content
                        .filter { it.supportsRemoteControl == true && it.id != null }
                sessions.count { session ->
                    try {
                        api.sessionApi.sendMessageCommand(
                            sessionId = session.id!!,
                            data = MessageCommand(header = "Message from Ben", text = text, timeoutMs = 15_000L),
                        )
                        true
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Could not send the pop-up to %s", session.deviceName)
                        false
                    }
                }
            }

        /** The libraries on the server (Movies, Shows, Anime...) */
        suspend fun libraries(): List<BaseItemDto> =
            io {
                api.libraryApi
                    .getMediaFolders(false)
                    .content.items
                    .sortedBy { it.name?.lowercase() }
            }

        /** Looks for new files: one library, or all of them when [libraryId] is null */
        suspend fun scan(libraryId: UUID?) =
            io {
                if (libraryId == null) {
                    api.libraryApi.refreshLibrary()
                } else {
                    api.itemRefreshApi.refreshItem(
                        itemId = libraryId,
                        metadataRefreshMode = MetadataRefreshMode.DEFAULT,
                        imageRefreshMode = MetadataRefreshMode.DEFAULT,
                    )
                }
            }

        suspend fun chores(): List<ServerChore> =
            io {
                val format = DateTimeFormatter.ofPattern("EEE h:mm a")
                api.scheduledTasksApi
                    .getTasks(isHidden = false)
                    .content
                    .filter { it.id != null }
                    .sortedWith(compareByDescending<TaskInfo> { it.state == TaskState.RUNNING }.thenBy { it.name })
                    .map { task ->
                        val result = task.lastExecutionResult
                        ServerChore(
                            id = task.id!!,
                            name = task.name.orEmpty(),
                            running = task.state != TaskState.IDLE,
                            progress = task.currentProgressPercentage?.toInt(),
                            lastRun =
                                result?.endTimeUtc?.let {
                                    it
                                        .atOffset(ZoneOffset.UTC)
                                        .atZoneSameInstant(ZoneId.systemDefault())
                                        .format(format)
                                },
                            failed = result?.status == TaskCompletionStatus.FAILED || result?.status == TaskCompletionStatus.ABORTED,
                            error = result?.errorMessage,
                        )
                    }
            }

        suspend fun runChore(id: String) = io { api.scheduledTasksApi.startTask(id) }

        suspend fun restartJellyfin() = io { api.systemApi.restartApplication() }

        /** How many screens a person can watch on at once (0 = no limit) */
        suspend fun setScreens(
            user: UserDto,
            screens: Int,
        ) = io {
            val policy =
                api.userApi
                    .getUserById(user.id)
                    .content.policy ?: return@io
            api.userApi.updateUserPolicy(user.id, policy.copy(maxActiveSessions = screens))
        }

        /** Which libraries a person can see; all of them when [folders] is null */
        suspend fun setLibraries(
            user: UserDto,
            folders: List<UUID>?,
        ) = io {
            val policy =
                api.userApi
                    .getUserById(user.id)
                    .content.policy ?: return@io
            api.userApi.updateUserPolicy(
                user.id,
                if (folders == null) {
                    policy.copy(enableAllFolders = true)
                } else {
                    policy.copy(enableAllFolders = false, enabledFolders = folders)
                },
            )
        }

        /** Signs a person out on every device except this one. Returns how many devices. */
        suspend fun signOutEverywhere(
            user: UserDto,
            thisDeviceId: String?,
        ): Int =
            io {
                val devices =
                    api.devicesApi
                        .getDevices(user.id)
                        .content.items
                        .filter { it.id != null && it.id != thisDeviceId }
                devices.count { device ->
                    try {
                        api.devicesApi.deleteDevice(device.id!!)
                        true
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Could not sign out %s", device.name)
                        false
                    }
                }
            }

        private suspend fun <T> io(block: suspend () -> T): T = withContext(WholphinDispatchers.IO) { block() }
    }
