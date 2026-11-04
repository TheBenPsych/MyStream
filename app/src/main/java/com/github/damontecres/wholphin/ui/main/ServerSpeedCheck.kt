package com.github.damontecres.wholphin.ui.main

import androidx.compose.foundation.lazy.LazyListScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.configurationApi
import org.jellyfin.sdk.api.client.extensions.environmentApi
import org.jellyfin.sdk.api.client.extensions.get
import org.jellyfin.sdk.api.client.extensions.post
import timber.log.Timber
import javax.inject.Inject

/** One line of the Tower speed check-up */
data class SpeedCheck(
    val id: String,
    val title: String,
    val ok: Boolean,
    /** What it means, in plain words */
    val detail: String,
    /** True when the Fix it button can change it from here */
    val canFix: Boolean = false,
)

data class SpeedCheckState(
    val running: Boolean = false,
    val checks: List<SpeedCheck>? = null,
    val problem: String? = null,
    val message: String? = null,
    /** Benflix: the message says a Fix it didn't work */
    val messageFailed: Boolean = false,
)

/**
 * Benflix: the Server page's "Tower speed check-up". Reads Jellyfin's video-converting settings and its
 * scene-picture (trickplay) settings, says in plain words what would make things faster, and can fix the
 * safe ones with one press.
 *
 * Settings are read and written as raw JSON, so settings this version of Benflix doesn't know about
 * (newer Jellyfin 12 ones) are kept exactly as they are.
 */
@HiltViewModel
class ServerSpeedCheckViewModel
    @Inject
    constructor(
        private val api: ApiClient,
    ) : ViewModel() {
        private val _state = MutableStateFlow(SpeedCheckState())
        val state: StateFlow<SpeedCheckState> = _state

        private val json = Json { ignoreUnknownKeys = true }

        /** [keepMessage]: true after a Fix it, so its message stays; Check again clears it */
        fun check(keepMessage: Boolean = false) {
            if (_state.value.running) return
            _state.value =
                if (keepMessage) {
                    _state.value.copy(running = true, problem = null)
                } else {
                    _state.value.copy(running = true, problem = null, message = null, messageFailed = false)
                }
            viewModelScope.launch {
                _state.value =
                    try {
                        SpeedCheckState(
                            checks = withContext(WholphinDispatchers.IO) { runChecks() },
                            message = _state.value.message,
                            messageFailed = _state.value.messageFailed,
                        )
                    } catch (e: Exception) {
                        Timber.w(e, "Tower speed check-up failed")
                        SpeedCheckState(
                            problem = "Couldn't read Jellyfin's settings (${e.javaClass.simpleName}). Are you signed in as an admin?",
                        )
                    }
            }
        }

        fun fix(id: String) {
            if (_state.value.running) return
            _state.value = _state.value.copy(running = true, message = null, messageFailed = false)
            viewModelScope.launch {
                var failed = false
                val message =
                    try {
                        withContext(WholphinDispatchers.IO) { applyFix(id) }
                    } catch (e: Exception) {
                        Timber.w(e, "Speed check-up fix %s failed", id)
                        failed = true
                        "Couldn't change that setting (${e.javaClass.simpleName})."
                    }
                _state.value = _state.value.copy(running = false, message = message, messageFailed = failed)
                check(keepMessage = true)
            }
        }

        private suspend fun encoding(): JsonObject =
            json
                .parseToJsonElement(
                    api.configurationApi
                        .getNamedConfiguration(ENCODING)
                        .content
                        .decodeToString(),
                ).jsonObject

        private suspend fun mainConfig(): JsonObject =
            json.parseToJsonElement(api.get<ByteArray>("/System/Configuration").content.decodeToString()).jsonObject

        private suspend fun exists(
            path: String,
            file: String? = null,
        ): Boolean =
            try {
                val entries =
                    api.environmentApi
                        .getDirectoryContents(path, includeFiles = file != null, includeDirectories = file == null)
                        .content
                if (file == null) true else entries.any { it.name == file }
            } catch (e: Exception) {
                false
            }

        private suspend fun runChecks(): List<SpeedCheck> {
            val enc = encoding()
            val trick = mainConfig()["TrickplayOptions"]?.jsonObject
            val accel = enc.str("HardwareAccelerationType")?.lowercase().orEmpty()
            val hw = accel == "qsv" || accel == "vaapi"
            val gpuSeen = exists("/dev/dri", "renderD128")
            val temp = enc.str("TranscodingTempPath").orEmpty()
            val ramFolder = exists(RAM_FOLDER)
            val inRam = temp.trimEnd('/') == RAM_FOLDER
            val codecs =
                (enc["HardwareDecodingCodecs"] as? JsonArray)
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull?.lowercase() }
                    .orEmpty()
            return buildList {
                add(
                    SpeedCheck(
                        id = FIX_ACCEL,
                        title = "Graphics chip converts videos",
                        ok = hw,
                        detail =
                            when {
                                hw -> {
                                    "On (${accel.uppercase()}). Tower's graphics chip does the work, so the processor stays free."
                                }

                                gpuSeen -> {
                                    "Off. Tower's processor does all the work. Fix it turns on Intel Quick Sync."
                                }

                                else -> {
                                    "Off, and Jellyfin can't see the graphics chip. Add the device /dev/dri to Jellyfin's " +
                                        "Docker settings on Unraid first, then check again."
                                }
                            },
                        canFix = !hw && gpuSeen,
                    ),
                )
                if (hw) {
                    add(
                        SpeedCheck(
                            id = FIX_ENCODE,
                            title = "Graphics chip makes the new video too",
                            ok = enc.bool("EnableHardwareEncoding") == true,
                            detail = "Lets the graphics chip build the smaller video, not only read the original.",
                            canFix = enc.bool("EnableHardwareEncoding") != true,
                        ),
                    )
                    val missing = WANTED_CODECS.filter { it !in codecs }
                    add(
                        SpeedCheck(
                            id = FIX_CODECS,
                            title = "Graphics chip reads every common format",
                            ok = missing.isEmpty(),
                            detail =
                                if (missing.isEmpty()) {
                                    "H.264, HEVC, VP9 and AV1 are all read by the graphics chip."
                                } else {
                                    "Not on for: ${missing.joinToString { it.uppercase() }}. Tower's chip can read these."
                                },
                            canFix = missing.isNotEmpty(),
                        ),
                    )
                    add(
                        SpeedCheck(
                            id = FIX_TONEMAP,
                            title = "HDR movies look right on normal screens",
                            ok = enc.bool("EnableTonemapping") == true,
                            detail = "When an HDR movie is converted for a phone or older TV, colours stay natural instead of washed out.",
                            canFix = enc.bool("EnableTonemapping") != true,
                        ),
                    )
                }
                val throttle = enc.bool("EnableThrottling") == true
                val deleteOld = enc.bool("EnableSegmentDeletion") == true
                add(
                    SpeedCheck(
                        id = FIX_THROTTLE,
                        title = "Converts only a little ahead",
                        ok = throttle && deleteOld,
                        detail =
                            "Jellyfin stops converting when it's far enough ahead and throws away the parts already watched. " +
                                "Less work for Tower and less space used.",
                        canFix = !(throttle && deleteOld),
                    ),
                )
                add(
                    SpeedCheck(
                        id = FIX_RAM,
                        title = "Converts in memory, not on the hard drives",
                        ok = inRam,
                        detail =
                            when {
                                temp.trimEnd('/') == RAM_FOLDER -> {
                                    "On. Converted video goes to Tower's memory, so it starts faster and the drives stay quiet."
                                }

                                ramFolder -> {
                                    "The memory folder is ready. Fix it tells Jellyfin to use it."
                                }

                                else -> {
                                    RAM_HOW
                                }
                            },
                        canFix = !inRam && ramFolder,
                    ),
                )
                if (trick != null && hw) {
                    val trickOk = trick.bool("EnableHwAcceleration") == true && trick.bool("EnableHwEncoding") == true
                    add(
                        SpeedCheck(
                            id = FIX_TRICKPLAY,
                            title = "Graphics chip makes the scene pictures",
                            ok = trickOk,
                            detail = "The little pictures you see while skipping through a movie are made much faster this way.",
                            canFix = !trickOk,
                        ),
                    )
                }
            }
        }

        private suspend fun applyFix(id: String): String {
            if (id == FIX_TRICKPLAY) {
                val config = mainConfig()
                val trick = config["TrickplayOptions"]?.jsonObject ?: return "Jellyfin has no scene picture settings."
                val newTrick = trick.with("EnableHwAcceleration" to JsonPrimitive(true), "EnableHwEncoding" to JsonPrimitive(true))
                api.post<Unit>("/System/Configuration", requestBody = config.with("TrickplayOptions" to newTrick))
                return "Scene pictures now use the graphics chip."
            }
            val enc = encoding()
            val changed: JsonObject =
                when (id) {
                    FIX_ACCEL -> {
                        enc.with(
                            "HardwareAccelerationType" to JsonPrimitive("qsv"),
                            "EnableHardwareEncoding" to JsonPrimitive(true),
                        )
                    }

                    FIX_ENCODE -> {
                        enc.with("EnableHardwareEncoding" to JsonPrimitive(true))
                    }

                    FIX_CODECS -> {
                        val have =
                            (enc["HardwareDecodingCodecs"] as? JsonArray)
                                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                                .orEmpty()
                        enc.with("HardwareDecodingCodecs" to JsonArray((have + WANTED_CODECS).distinct().map { JsonPrimitive(it) }))
                    }

                    FIX_TONEMAP -> {
                        enc.with("EnableTonemapping" to JsonPrimitive(true))
                    }

                    FIX_THROTTLE -> {
                        enc.with("EnableThrottling" to JsonPrimitive(true), "EnableSegmentDeletion" to JsonPrimitive(true))
                    }

                    FIX_RAM -> {
                        enc.with("TranscodingTempPath" to JsonPrimitive(RAM_FOLDER))
                    }

                    else -> {
                        return "Nothing to change."
                    }
                }
            api.configurationApi.updateNamedConfiguration(ENCODING, changed)
            return "Done. Jellyfin uses the new setting for the next video it converts."
        }

        private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

        private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

        private fun JsonObject.with(vararg pairs: Pair<String, JsonElement>): JsonObject = JsonObject(this + pairs)

        companion object {
            private const val ENCODING = "encoding"

            /** The memory folder Ben adds to the Jellyfin container on Unraid */
            const val RAM_FOLDER = "/transcode"

            private const val RAM_ON =
                "On. Converted video goes to Tower's memory, so it starts faster and the drives stay quiet."
            private const val RAM_HOW =
                "Not set up. On Unraid: Docker > Jellyfin > Edit > Advanced View, add this to Extra Parameters: " +
                    "--mount type=tmpfs,destination=/transcode,tmpfs-size=8G  then Apply, and check again."

            private val WANTED_CODECS = listOf("h264", "hevc", "vp9", "av1")

            private const val FIX_ACCEL = "accel"
            private const val FIX_ENCODE = "encode"
            private const val FIX_CODECS = "codecs"
            private const val FIX_TONEMAP = "tonemap"
            private const val FIX_THROTTLE = "throttle"
            private const val FIX_RAM = "ram"
            private const val FIX_TRICKPLAY = "trickplay"
        }
    }

/** Benflix: the "Tower speed check-up" part of the Server page's Tools section */
internal fun LazyListScope.speedCheckItems(
    state: SpeedCheckState,
    viewModel: ServerSpeedCheckViewModel,
) {
    item { SectionTitle("Tower speed check-up") }
    item {
        ServerActionRow(
            headline = "Checks Jellyfin's settings that make videos start and skip faster",
            supporting =
                when {
                    state.running -> "Checking…"
                    state.checks == null -> "Press Check to look."
                    state.checks.all { it.ok } -> "Everything is set up for speed."
                    else -> "${state.checks.count { !it.ok }} thing(s) could be faster."
                },
            actions = listOf((if (state.checks == null) "Check" else "Check again") to { viewModel.check() }),
        )
    }
    state.problem?.let { problem ->
        item { ServerActionRow(headline = "Couldn't check", supporting = problem, leading = { Dot(Light.BAD) }) }
    }
    state.message?.let { message ->
        item { ServerActionRow(headline = message, leading = { Dot(if (state.messageFailed) Light.BAD else Light.GOOD) }) }
    }
    state.checks?.forEach { check ->
        item(key = "speed-${check.id}") {
            ServerActionRow(
                headline = check.title,
                supporting = check.detail,
                leading = { Dot(if (check.ok) Light.GOOD else Light.WARN) },
                actions = if (check.canFix && !state.running) listOf("Fix it" to { viewModel.fix(check.id) }) else emptyList(),
            )
        }
    }
}
